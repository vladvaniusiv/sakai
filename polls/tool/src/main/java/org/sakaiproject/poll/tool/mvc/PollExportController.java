package org.sakaiproject.poll.tool.mvc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.sakaiproject.poll.api.model.Option;
import org.sakaiproject.poll.api.model.Poll;
import org.sakaiproject.poll.api.model.Vote;
import org.sakaiproject.poll.api.service.PollsService;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
@Slf4j
public class PollExportController {

    private final PollsService pollsService;
    private final MessageSource messageSource;

    @GetMapping("/polls/export/xlsx/{pollId}")
    public ResponseEntity<byte[]> exportXlsx(@PathVariable("pollId") String pollId,
                                             Locale locale) {

        Optional<Poll> pollOpt = pollsService.getPollById(pollId);
        if (pollOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Poll poll = pollOpt.get();
        List<Option> options = poll.getOptions();
        List<Vote> allVotes = pollsService.getAllVotesForPoll(poll.getId());
        int totalVotes = allVotes.size();

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet("Poll Results");

            Font boldFont = wb.createFont();
            boldFont.setBold(true);

            CellStyle boldStyle = wb.createCellStyle();
            boldStyle.setFont(boldFont);

            Row title1 = sheet.createRow(0);
            title1.createCell(0).setCellValue(messageSource.getMessage("poll_export_title", null, locale));
            title1.getCell(0).setCellStyle(boldStyle);

            Row title2 = sheet.createRow(2);
            title2.createCell(0).setCellValue(
                    messageSource.getMessage("poll_export_poll_label", null, locale) + " " + poll.getText()
            );
            title2.getCell(0).setCellStyle(boldStyle);

            String formattedDate = LocalDateTime.now().format(
                    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale)
            );

            Row title3 = sheet.createRow(3);
            title3.createCell(0).setCellValue(
                    messageSource.getMessage("poll_export_download_date", null, locale) + " " + formattedDate
            );
            title3.getCell(0).setCellStyle(boldStyle);

            Row header = sheet.createRow(5);

            Cell c0 = header.createCell(0);
            c0.setCellValue(messageSource.getMessage("poll_export_header_option", null, locale));
            c0.setCellStyle(boldStyle);

            Cell c1 = header.createCell(1);
            c1.setCellValue(messageSource.getMessage("poll_export_header_votes", null, locale));
            c1.setCellStyle(boldStyle);

            Cell c2 = header.createCell(2);
            c2.setCellValue(messageSource.getMessage("poll_export_header_percentage", null, locale));
            c2.setCellStyle(boldStyle);

            int rowNum = 6;
            for (Option opt : options) {
                long votes = allVotes.stream()
                        .filter(v -> v.getOption().getId().equals(opt.getId()))
                        .count();

                double percent = totalVotes == 0 ? 0 : (votes * 100.0 / totalVotes);

                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(opt.getText());
                row.createCell(1).setCellValue(votes);
                row.createCell(2).setCellValue(String.format("%.2f%%", percent));
            }

            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            sheet.autoSizeColumn(2);

            wb.write(bos);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String filename = "poll_results_" + timestamp + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(bos.toByteArray());

        } catch (Exception e) {
            log.error("Error generating XLSX for poll {}", pollId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}