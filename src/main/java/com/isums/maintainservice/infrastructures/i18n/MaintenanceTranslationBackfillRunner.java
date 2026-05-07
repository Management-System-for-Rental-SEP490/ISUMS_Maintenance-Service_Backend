package com.isums.maintainservice.infrastructures.i18n;

import com.isums.maintainservice.domains.entities.InspectionJob;
import com.isums.maintainservice.domains.entities.MaintenanceExecution;
import com.isums.maintainservice.domains.entities.PeriodicInspectionPlan;
import com.isums.maintainservice.infrastructures.repositories.InspectionJobRepository;
import com.isums.maintainservice.infrastructures.repositories.MaintenanceExecutionRepository;
import com.isums.maintainservice.infrastructures.repositories.PeriodicInspectionPlanRepository;
import com.isums.maintainservice.services.TranslationAutoFillService;
import common.i18n.TranslationMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * One-shot backfill: populate *_translations columns from existing VI text.
 * Enable with:  maintenance.i18n.backfill.enabled=true
 * Assumes legacy text is Vietnamese (the historical default).
 * Idempotent — skips rows that already have a non-empty translation map.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MaintenanceTranslationBackfillRunner implements ApplicationRunner {

    private static final String LEGACY_SOURCE = "vi";

    private final PeriodicInspectionPlanRepository planRepository;
    private final InspectionJobRepository inspectionRepository;
    private final MaintenanceExecutionRepository executionRepository;
    private final TranslationAutoFillService translationAutoFillService;

    @Value("${maintenance.i18n.backfill.enabled:false}")
    private boolean enabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("MaintenanceTranslationBackfillRunner: disabled, skipping");
            return;
        }
        log.info("MaintenanceTranslationBackfillRunner: starting backfill");
        int plans = backfillPlans();
        int inspections = backfillInspections();
        int executions = backfillExecutions();
        log.info("MaintenanceTranslationBackfillRunner: done — plans={} inspections={} executions={}",
                plans, inspections, executions);
    }

    private int backfillPlans() {
        List<PeriodicInspectionPlan> all = planRepository.findAll();
        int count = 0;
        for (PeriodicInspectionPlan plan : all) {
            if (hasTranslations(plan.getNameTranslations())) continue;
            if (plan.getName() == null || plan.getName().isBlank()) continue;
            plan.setNameTranslations(translationAutoFillService.complete(plan.getName(), LEGACY_SOURCE));
            if (plan.getSourceLanguage() == null || plan.getSourceLanguage().isBlank()) {
                plan.setSourceLanguage(LEGACY_SOURCE);
            }
            planRepository.save(plan);
            count++;
        }
        return count;
    }

    private int backfillInspections() {
        List<InspectionJob> all = inspectionRepository.findAll();
        int count = 0;
        for (InspectionJob job : all) {
            if (hasTranslations(job.getNoteTranslations())) continue;
            if (job.getNote() == null || job.getNote().isBlank()) continue;
            job.setNoteTranslations(translationAutoFillService.complete(job.getNote(), LEGACY_SOURCE));
            if (job.getSourceLanguage() == null || job.getSourceLanguage().isBlank()) {
                job.setSourceLanguage(LEGACY_SOURCE);
            }
            inspectionRepository.save(job);
            count++;
        }
        return count;
    }

    private int backfillExecutions() {
        List<MaintenanceExecution> all = executionRepository.findAll();
        int count = 0;
        for (MaintenanceExecution ex : all) {
            if (hasTranslations(ex.getNotesTranslations())) continue;
            if (ex.getNotes() == null || ex.getNotes().isBlank()) continue;
            ex.setNotesTranslations(translationAutoFillService.complete(ex.getNotes(), LEGACY_SOURCE));
            if (ex.getSourceLanguage() == null || ex.getSourceLanguage().isBlank()) {
                ex.setSourceLanguage(LEGACY_SOURCE);
            }
            executionRepository.save(ex);
            count++;
        }
        return count;
    }

    private static boolean hasTranslations(TranslationMap map) {
        return map != null && !map.getTranslations().isEmpty();
    }
}
