package com.sequenceiq.cloudbreak.job.stackpatcher;

import static com.sequenceiq.cloudbreak.job.stackpatcher.ExistingStackPatcherJobAdapter.STACK_PATCH_TYPE_NAME;

import java.util.Collection;
import java.util.Optional;

import javax.inject.Inject;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.api.endpoint.v4.common.Status;
import com.sequenceiq.cloudbreak.domain.converter.StackPatchTypeConverter;
import com.sequenceiq.cloudbreak.domain.stack.Stack;
import com.sequenceiq.cloudbreak.domain.stack.StackPatchType;
import com.sequenceiq.cloudbreak.domain.view.StackView;
import com.sequenceiq.cloudbreak.quartz.statuschecker.job.StatusCheckerJob;
import com.sequenceiq.cloudbreak.service.stack.StackService;
import com.sequenceiq.cloudbreak.service.stack.StackViewService;
import com.sequenceiq.cloudbreak.service.stackpatch.ExistingStackPatchApplyException;
import com.sequenceiq.cloudbreak.service.stackpatch.ExistingStackPatchService;
import com.sequenceiq.cloudbreak.service.stackpatch.StackPatchUsageReporterService;

import io.opentracing.Tracer;

@DisallowConcurrentExecution
@Component
public class ExistingStackPatcherJob extends StatusCheckerJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExistingStackPatcherJob.class);

    @Inject
    private StackViewService stackViewService;

    @Inject
    private StackService stackService;

    @Inject
    private StackPatchTypeConverter stackPatchTypeConverter;

    @Inject
    private ExistingStackPatcherJobService jobService;

    @Inject
    private Collection<ExistingStackPatchService> existingStackPatchServices;

    @Inject
    private StackPatchUsageReporterService stackPatchUsageReporterService;

    public ExistingStackPatcherJob(Tracer tracer) {
        super(tracer, "Existing Stack Patcher Job");
    }

    @Override
    protected Object getMdcContextObject() {
        return stackViewService.findById(getStackId()).orElseGet(StackView::new);
    }

    @Override
    protected void executeTracedJob(JobExecutionContext context) throws JobExecutionException {
        Stack stack = stackService.getByIdWithListsInTransaction(getStackId());
        Status stackStatus = stack.getStatus();
        String stackPatchTypeName = context.getJobDetail().getJobDataMap().getString(STACK_PATCH_TYPE_NAME);
        StackPatchType stackPatchType = stackPatchTypeConverter.convertToEntityAttribute(stackPatchTypeName);
        if (!Status.getUnschedulableStatuses().contains(stackStatus)) {
            if (stackPatchType == null || StackPatchType.UNKNOWN.equals(stackPatchType)) {
                String message = String.format("Stack patch type %s is unknown", stackPatchTypeName);
                unscheduleAndFailJob(message, context, stack, stackPatchType);
            } else {
                Optional<ExistingStackPatchService> optionalExistingStackPatchService = getStackPatchServiceForType(stackPatchType);
                if (optionalExistingStackPatchService.isEmpty()) {
                    String message = "No stack patcher implementation found for type " + stackPatchType;
                    unscheduleAndFailJob(message, context, stack, stackPatchType);
                } else {
                    boolean success = applyStackPatch(optionalExistingStackPatchService.get(), stack);
                    if (success) {
                        unscheduleJob(context, stack, stackPatchType);
                    }
                }
            }
        } else {
            LOGGER.debug("Existing stack patching will be unscheduled, because stack {} status is {}", stack.getResourceCrn(), stackStatus);
            unscheduleJob(context, stack, stackPatchType);
        }
    }

    private void unscheduleAndFailJob(String message, JobExecutionContext context, Stack stack, StackPatchType stackPatchType)
            throws JobExecutionException {
        LOGGER.info("Unscheduling and failing stack patcher {} for stack {} with message: {}", stackPatchType, stack.getResourceCrn(), message);
        unscheduleJob(context, stack, stackPatchType);
        stackPatchUsageReporterService.reportFailure(stack, stackPatchType, message);
        throw new JobExecutionException(message);
    }

    private void unscheduleJob(JobExecutionContext context, Stack stack, StackPatchType stackPatchType) {
        LOGGER.info("Unscheduling stack patcher {} job for stack {}", stackPatchType, stack);
        jobService.unschedule(context.getJobDetail().getKey());
    }

    private Optional<ExistingStackPatchService> getStackPatchServiceForType(StackPatchType stackPatchType) {
        return existingStackPatchServices.stream()
                .filter(existingStackPatchService -> stackPatchType.equals(existingStackPatchService.getStackPatchType()))
                .findFirst();
    }

    private boolean applyStackPatch(ExistingStackPatchService existingStackPatchService, Stack stack) throws JobExecutionException {
        StackPatchType stackPatchType = existingStackPatchService.getStackPatchType();
        if (!existingStackPatchService.isStackAlreadyFixed(stack)) {
            try {
                if (existingStackPatchService.isAffected(stack)) {
                    LOGGER.debug("Stack {} needs patch for {}", stack.getResourceCrn(), stackPatchType);
                    stackPatchUsageReporterService.reportAffected(stack, stackPatchType);
                    boolean success = existingStackPatchService.apply(stack);
                    if (success) {
                        stackPatchUsageReporterService.reportSuccess(stack, stackPatchType);
                    }
                    return success;
                } else {
                    LOGGER.debug("Stack {} is not affected by {}", stack.getResourceCrn(), stackPatchType);
                    return true;
                }
            } catch (ExistingStackPatchApplyException e) {
                String message = String.format("Failed to patch stack %s for %s", stack.getResourceCrn(), stackPatchType);
                LOGGER.error(message, e);
                stackPatchUsageReporterService.reportFailure(stack, stackPatchType, e.getMessage());
                throw new JobExecutionException(message, e);
            }
        } else {
            LOGGER.debug("Stack {} was already patched for {}", stack.getResourceCrn(), stackPatchType);
            return true;
        }
    }

    private Long getStackId() {
        return Long.valueOf(getLocalId());
    }
}
