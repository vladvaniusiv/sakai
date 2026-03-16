package org.sakaiproject.poll.tool.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.sakaiproject.poll.tool.model.PollBatchItem;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BatchPollFileParser {

    public List<PollBatchItem> parse(MultipartFile file) {
        String filename = file.getOriginalFilename().toLowerCase();

        try {
            // Route file to the correct parser based on extension
            if (filename.endsWith(".csv")) {
                return parseCsv(file.getInputStream());
            } else if (filename.endsWith(".xls") || filename.endsWith(".xlsx")) {
                return parseExcel(file.getInputStream());
            } else {
                throw new IllegalArgumentException("Unsupported file type: " + filename);
            }
        } catch (Exception e) {
            // Wrap any parsing failure with a user‑friendly message
            throw new RuntimeException("Error parsing file: " + e.getMessage(), e);
        }
    }

    private List<PollBatchItem> parseCsv(InputStream input) {
        int index = 0;
        List<PollBatchItem> items = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {

            String line;
            int rowNum = 0;

            while ((line = reader.readLine()) != null) {
                rowNum++;

                // Skip header row
                if (rowNum == 1) continue;

                // Skip empty lines
                if (line.trim().isEmpty()) continue;

                // Detect separator dynamically (comma, semicolon, etc.)
                char separator = detectSeparator(line);

                // Split CSV respecting quoted fields (e.g., "Option, with comma")
                String[] cols = splitRespectingQuotes(line, separator);

                PollBatchItem item = new PollBatchItem();

                // Basic structural validation: at least 8 required columns
                if (cols.length < 8) {
                    item.getErrors().add("Invalid CSV format: not enough columns");
                    items.add(item);
                    continue;
                }

                // Parse date+time in multiple supported formats
                LocalDateTime odt = parseCsvDateTime(cols[1]);
                LocalDateTime cdt = parseCsvDateTime(cols[2]);

                DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

                // Store raw LocalDateTime for poll creation
                item.setOpenDateTime(odt);
                item.setCloseDateTime(cdt);

                // Also store LocalDate for validation logic
                item.setOpenDate(odt != null ? odt.toLocalDate() : null);
                item.setCloseDate(cdt != null ? cdt.toLocalDate() : null);

                // Pre‑formatted strings for safe Thymeleaf display
                item.setOpenDateDisplay(odt != null ? odt.format(DISPLAY) : "—");
                item.setCloseDateDisplay(cdt != null ? cdt.format(DISPLAY) : "—");

                item.setQuestion(clean(cols[0]));

                item.setMinOptions(parseCsvInt(cols[3]));
                item.setMaxOptions(parseCsvInt(cols[4]));

                // CSV uses "false" to indicate non‑public; anything else = public
                String rawPublic = cols[5].trim().toLowerCase();
                boolean isPublic = !rawPublic.equals("false");
                item.setPublic(isPublic);

                // If poll is public, ignore group column entirely
                if (isPublic) {
                    item.setGroupIds(new ArrayList<>());
                } else {
                    item.setGroupIds(parseGroups(clean(cols[6])));
                }

                item.setResultsVisibility(normalizeVisibility(clean(cols[7])));

                // Parse all remaining columns as poll options
                List<String> options = new ArrayList<>();
                for (int i = 8; i < cols.length; i++) {
                    String opt = clean(cols[i]);
                    if (opt != null && !opt.isEmpty()) {
                        options.add(opt);
                    }
                }
                item.setOptions(options);

                index++;
                item.setRowNumber(index);

                items.add(item);
            }

        } catch (Exception e) {
            throw new RuntimeException("Error reading CSV file", e);
        }

        return items;
    }


    private LocalDateTime parseCsvDateTime(String raw) {
        if (raw == null) return null;
        raw = raw.trim().replace("\"", ""); // Remove surrounding quotes

        try {
            // Full ISO‑8601 with UTC marker (Z)
            if (raw.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")) {
                DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
                return LocalDateTime.parse(raw, f);
            }

            // ISO‑8601 without timezone (common in CSV exports)
            if (raw.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")) {
                DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
                return LocalDateTime.parse(raw, f);
            }

            // ISO date with space‑separated time
            if (raw.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
                DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                return LocalDateTime.parse(raw, f);
            }

            // US format with time (MM/dd/yyyy HH:mm:ss)
            if (raw.matches("\\d{1,2}/\\d{1,2}/\\d{4} \\d{2}:\\d{2}:\\d{2}")) {
                // Heuristic: if first number > 12 → must be dd/MM/yyyy
                int day = Integer.parseInt(raw.split("[/ ]")[0]);
                if (day > 12) {
                    return LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
                }
                try {
                    return LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
                } catch (Exception ignored) {}
                return LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"));
            }

            // European format with time (dd-MM-yyyy HH:mm:ss)
            if (raw.matches("\\d{1,2}-\\d{1,2}-\\d{4} \\d{2}:\\d{2}:\\d{2}")) {
                return LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
            }

            // ISO date only → default to midnight
            if (raw.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(raw).atStartOfDay();
            }

            // dd/MM/yyyy without time
            if (raw.matches("\\d{1,2}/\\d{1,2}/\\d{4}")) {
                int day = Integer.parseInt(raw.split("/")[0]);
                if (day > 12) {
                    return LocalDate.parse(raw, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                                    .atStartOfDay();
                }
                try {
                    return LocalDate.parse(raw, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                                    .atStartOfDay();
                } catch (Exception ignored) {}
                return LocalDate.parse(raw, DateTimeFormatter.ofPattern("MM/dd/yyyy"))
                                .atStartOfDay();
            }

            // dd-MM-yyyy without time
            if (raw.matches("\\d{1,2}-\\d{1,2}-\\d{4}")) {
                return LocalDate.parse(raw, DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                                .atStartOfDay();
            }

        } catch (Exception ignored) {
            // Any parsing failure returns null → validation will catch it
        }

        return null;
    }


    private Integer parseCsvInt(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private List<PollBatchItem> parseExcel(InputStream input) {
        int index = 0;
        List<PollBatchItem> items = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(input)) {
            Sheet sheet = workbook.getSheetAt(0);

            int rowNum = 0;
            for (Row row : sheet) {
                rowNum++;

                // Skip header row
                if (rowNum == 1) continue;

                PollBatchItem item = new PollBatchItem();

                item.setQuestion(clean(getString(row, 0)));

                // Parse Excel date/time using both numeric and string formats
                LocalDateTime odt = parseExcelDateTime(row, 1);
                LocalDateTime cdt = parseExcelDateTime(row, 2);

                DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

                // Store raw LocalDateTime for poll creation
                item.setOpenDateTime(odt);
                item.setCloseDateTime(cdt);

                // Store LocalDate for validation logic
                item.setOpenDate(odt != null ? odt.toLocalDate() : null);
                item.setCloseDate(cdt != null ? cdt.toLocalDate() : null);

                // Preformatted strings for safe Thymeleaf rendering
                item.setOpenDateDisplay(odt != null ? odt.format(DISPLAY) : "—");
                item.setCloseDateDisplay(cdt != null ? cdt.format(DISPLAY) : "—");

                item.setMinOptions(getInt(row, 3));
                item.setMaxOptions(getInt(row, 4));
                String rawPublic = clean(getString(row, 5));
                boolean isPublic = rawPublic == null || !rawPublic.trim().equalsIgnoreCase("false");
                item.setPublic(isPublic);

                // Public polls ignore group restrictions entirely
                if (isPublic) {
                    item.setGroupIds(new ArrayList<>());
                } else {
                    item.setGroupIds(parseGroups(getString(row, 6)));
                }
                item.setResultsVisibility(clean(getString(row, 7)));

                // Parse all remaining columns as poll options
                item.setOptions(parseOptions(row, 8));

                index++;
                item.setRowNumber(index);

                items.add(item);
            }

        } catch (Exception e) {
            throw new RuntimeException("Error reading Excel file", e);
        }

        return items;
    }

    private String getString(Row row, int col) {
        Cell cell = row.getCell(col);
        // Convert any Excel cell type to a cleaned string representation
        return cell == null ? null : clean(cell.toString());
    }

    private Integer getInt(Row row, int col) {
        try {
            // Excel stores numbers as doubles; cast to int safely
            return (int) row.getCell(col).getNumericCellValue();
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime parseExcelDateTime(Row row, int col) {
        try {
            Cell cell = row.getCell(col);
            if (cell == null) return null;

            // Excel stores real date/time values as numeric cells with a date format
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue();
            }

            // If the cell is text, reuse the CSV date parser (supports many formats)
            if (cell.getCellType() == CellType.STRING) {
                return parseCsvDateTime(cell.getStringCellValue());
            }

            return null;

        } catch (Exception e) {
            // Any parsing failure returns null; validation will catch missing dates
            return null;
        }
    }

    private List<String> parseGroups(String raw) {
        if (raw == null || raw.isBlank()) return new ArrayList<>();
        // Groups are comma‑separated; each must be cleaned individually
        String[] parts = raw.split(",");
        List<String> groups = new ArrayList<>();
        for (String p : parts) groups.add(clean(p));
        return groups;
    }

    private List<String> parseOptions(Row row, int startCol) {
        List<String> options = new ArrayList<>();

        // Read all columns from startCol to the last non-empty cell
        for (int col = startCol; col < row.getLastCellNum(); col++) {
            String val = getString(row, col);
            if (val != null && !val.isBlank()) {
                options.add(clean(val));
            }
        }
        return options;
    }

    private String normalizeVisibility(String raw) {
        if (raw == null) return "never";

        raw = clean(raw).toLowerCase();

        // Map many possible user inputs to the internal values used by the editor
        switch (raw) {
            case "always":
            case "open":
            case "visible":
            case "siempre":
                return "open";

            case "afterclose":
            case "after closing":
            case "afterclosing":
            case "despuesdecerrar":
            case "after_close":
                return "afterClosing";

            case "aftervoting":
            case "after voting":
            case "after_vote":
                return "afterVoting";

            default:
                return "never";
        }
    }

    private String[] splitRespectingQuotes(String line, char separator) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        // Manual CSV parser that respects quoted fields containing separators
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes; // toggle quote state
            } else if (c == separator && !inQuotes) {
                // Separator outside quotes → end of field
                result.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        // Add final field
        result.add(current.toString().trim());
        return result.toArray(new String[0]);
    }

    private char detectSeparator(String line) {
        // If only one separator appears, choose it directly
        if (line.contains(";") && !line.contains(",")) return ';';
        if (line.contains(",") && !line.contains(";")) return ',';

        // Otherwise choose the most frequent one
        long commas = line.chars().filter(ch -> ch == ',').count();
        long semis = line.chars().filter(ch -> ch == ';').count();
        return semis > commas ? ';' : ',';
    }

    private String clean(String raw) {
        if (raw == null) return null;
        return raw.replace("\"\"", "\"") // unescape double quotes
                .replace("\"", "") // remove surrounding quotes
                .trim();
    }
}
