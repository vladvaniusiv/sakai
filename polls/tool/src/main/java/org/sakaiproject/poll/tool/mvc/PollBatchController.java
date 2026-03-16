package org.sakaiproject.poll.tool.mvc;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.poll.api.model.Option;
import org.sakaiproject.poll.api.model.Poll;
import org.sakaiproject.poll.api.service.PollsService;
import org.sakaiproject.poll.tool.model.PollBatchItem;
import org.sakaiproject.poll.tool.util.BatchPollFileParser;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.tool.api.ToolManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/batch")
@SessionAttributes("items")
@Slf4j
public class PollBatchController {

    @GetMapping
    public String showBatchUpload(Model model) {
        model.addAttribute("csvTemplatePath", "/batch/template");
        return "polls/batch";
    }

    @GetMapping("/template")
    public ResponseEntity<Resource> downloadTemplate() throws IOException {
        // Serves the CSV template bundled inside the tool's resources
        ClassPathResource resource = new ClassPathResource("META-INF/resources/poll-batch-template.csv");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=poll-batch-template.csv")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(resource);
    }


    @Autowired
    private SiteService siteService;
    @Autowired
    private PollsService pollsService;
    @Autowired
    private ToolManager toolManager;    
    @Autowired
    private MessageSource messageSource;

    @PostMapping("/upload")
    public String handleBatchUpload(@RequestParam("file") MultipartFile file, Model model) {

        BatchPollFileParser parser = new BatchPollFileParser();
        List<PollBatchItem> items = parser.parse(file); // parse CSV/XLSX into structured rows

        validateBatchItems(items); // add validation errors to each row

        model.addAttribute("items", items); // stored in session due to @SessionAttributes

        return "polls/batch"; // redisplay table with parsed results
    }

    private void validateBatchItems(List<PollBatchItem> items) {

        for (PollBatchItem item : items) {

            if (item.getQuestion() == null || item.getQuestion().isBlank()) {
                item.getErrors().add(msg("batch.error.question_required"));
            }

            // Validate date presence and ordering
            if (item.getOpenDate() == null || item.getCloseDate() == null) {
                item.getErrors().add(msg("batch.error.dates_required"));
            } else if (item.getOpenDate().isAfter(item.getCloseDate())) {
                item.getErrors().add(msg("batch.error.date_order"));
            }

            // Validate min/max options
            if (item.getMinOptions() == null || item.getMaxOptions() == null) {
                item.getErrors().add(msg("batch.error.minmax_required"));
            } else if (item.getMinOptions() > item.getMaxOptions()) {
                item.getErrors().add(msg("batch.error.minmax_invalid"));
            }

            // Require at least two answer options
            if (item.getOptions().size() < 2) {
                item.getErrors().add(msg("batch.error.options_required"));
            }

            // Validate group names against actual site groups
            if (!item.getGroupIds().isEmpty()) {

                String siteId = toolManager.getCurrentPlacement().getContext();

                Set<String> validGroupNames = new HashSet<>();

                try {
                    validGroupNames = siteService.getSite(siteId)
                            .getGroups()
                            .stream()
                            .map(Group::getTitle)
                            .collect(Collectors.toSet());
                } catch (IdUnusedException e) {
                    // Site not found → cannot validate groups
                    item.getErrors().add(msg("batch.error.site_not_found", siteId));
                    continue;
                }

                // Check each group name from CSV
                for (String g : item.getGroupIds()) {
                    if (!validGroupNames.contains(g)) {
                        item.getErrors().add(msg("batch.error.group_not_found", g));
                    }
                }
            }
        }
    }

    @PostMapping("/create")
    public String createPollsFromBatch(@ModelAttribute("items") List<PollBatchItem> items,
                                    RedirectAttributes redirectAttributes,
                                    Model model,
                                    org.springframework.web.bind.support.SessionStatus status) {

        int created = 0;
        int failed = 0;

        for (PollBatchItem item : items) {

            // Skip rows with validation errors
            if (!item.getErrors().isEmpty()) {
                failed++;
                continue;
            }

            try {
                Poll poll = new Poll();

                poll.setText(item.getQuestion());
                poll.setSiteId(toolManager.getCurrentPlacement().getContext());

                poll.setLimitVoting(true);
                poll.setDescription(" "); // Polls requires a non-null description

                ZoneId zone = ZoneId.systemDefault();

                // Convert LocalDateTime → Instant using server timezone
                LocalDateTime odt = item.getOpenDateTime();
                LocalDateTime cdt = item.getCloseDateTime();

                poll.setVoteOpen(odt.atZone(zone).toInstant());
                poll.setVoteClose(cdt.atZone(zone).toInstant());

                poll.setMinOptions(item.getMinOptions());
                poll.setMaxOptions(item.getMaxOptions());

                poll.setDisplayResult(item.getResultsVisibility());
                poll.setPublic(item.isPublic());

                // Resolve group names from CSV to actual Sakai group IDs
                String siteId = toolManager.getCurrentPlacement().getContext();
                Map<String, String> groupNameToId = siteService.getSite(siteId)
                        .getGroups()
                        .stream()
                        .collect(Collectors.toMap(Group::getTitle, Group::getId));

                // Convert CSV group names → real group IDs
                Set<String> resolvedGroupIds = item.getGroupIds()
                        .stream()
                        .map(name -> groupNameToId.get(name)) // null if invalid
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

                poll.setGroupIds(resolvedGroupIds);

                // Add poll options
                for (String optText : item.getOptions()) {
                    Option opt = new Option();
                    opt.setText(optText);
                    poll.addOption(opt);
                }

                pollsService.savePoll(poll);
                created++;

            } catch (Exception e) {
                failed++;
                // Attach creation error to the row for user feedback
                item.getErrors().add(msg("batch.error.creation_failed", e.getMessage()));
            }
        }

        redirectAttributes.addFlashAttribute("created", created);
        redirectAttributes.addFlashAttribute("failed", failed);

        // Clear @SessionAttributes("items")
        status.setComplete();

        return "redirect:/batch";
    }

    private String msg(String key, Object... args) {
        // Resolve i18n messages using the current locale
        return messageSource.getMessage(
            key,
            args,
            LocaleContextHolder.getLocale()
        );
    }
}
