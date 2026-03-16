package org.sakaiproject.poll.tool.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class PollBatchItem {

    private String question;
    private LocalDate openDate;
    private LocalDate closeDate;

    private Integer minOptions;
    private Integer maxOptions;

    private boolean isPublic;
    private List<String> groupIds = new ArrayList<>();

    private String resultsVisibility; // always, afterClose, never

    private List<String> options = new ArrayList<>();

    private int rowNumber; // row index in the uploaded file
    private List<String> errors = new ArrayList<>();

    // Parsed date+time from CSV/XLSX (kept separately from LocalDate)
    private LocalDateTime openDateTime;
    private LocalDateTime closeDateTime;

    // Pre‑formatted date+time strings for safe display in Thymeleaf
    private String openDateDisplay;
    private String closeDateDisplay;

}
