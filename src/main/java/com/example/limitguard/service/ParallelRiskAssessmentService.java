package com.example.limitguard.service;

import com.example.limitguard.dto.CompanyRiskAssessmentResponse;
import com.example.limitguard.dto.MlRiskPredictionResponse;
import com.example.limitguard.model.CorporateGroupMembership;
import com.example.limitguard.model.CreditLimit;
import com.example.limitguard.repository.CorporateGroupMembershipRepository;
import com.example.limitguard.repository.CorporateGroupRepository;
import com.example.limitguard.repository.CreditLimitRepository;
import com.example.limitguard.security.InstitutionAccessService;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.example.limitguard.exception.CorporateGroupNotFoundException;
import com.example.limitguard.exception.ParallelRiskAssessmentException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
@RequiredArgsConstructor
public class ParallelRiskAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(ParallelRiskAssessmentService.class);

    // gets the companies belonging to a corporate group
    private final CorporateGroupMembershipRepository membershipRepository;

    // checks whether the corporate group exists
    private final CorporateGroupRepository corporateGroupRepository;

    // checks which companies are linked to the logged-in user's bank
    private final CreditLimitRepository creditLimitRepository;

    // gets the logged-in user's bank id
    private final InstitutionAccessService institutionAccessService;

    // sends company financial information to the python ml model
    private final MlRiskPredictionService mlRiskPredictionService;

    //---------------------------------------------------------------------

    // assesses multiple companies in a corporate group using multithreading
    public List<CompanyRiskAssessmentResponse> assessGroup(Long groupId) {
        long batchStart = System.nanoTime();
        boolean completed = false;
        log.info("ML BATCH START | Group ID={} | Thread={}", groupId, Thread.currentThread().getName());
        try {
            List<CompanyRiskAssessmentResponse> results = assessGroupWithWorkers(groupId);
            completed = true;
            return results;
        } finally {
            log.info("ML BATCH COMPLETE | Group ID={} | Thread={} | Outcome={} | Total elapsed ms={} | Includes authorization and worker collection",
                    groupId, Thread.currentThread().getName(), completed ? "COMPLETED" : "FAILED",
                    (System.nanoTime() - batchStart) / 1_000_000.0);
        }
    }

    private List<CompanyRiskAssessmentResponse> assessGroupWithWorkers(Long groupId) {

        // make sure the requested corporate group exists
        if (!corporateGroupRepository.existsById(groupId)) {
            throw new CorporateGroupNotFoundException(
                    "Corporate group not found with ID: " + groupId
            );
        }

        // get the logged-in user's bank id before starting any worker threads
        // worker threads do not automatically have the user's spring security context
        Long institutionId = institutionAccessService.getCurrentInstitutionId();

        // get all company memberships belonging to the corporate group
        List<CorporateGroupMembership> memberships =
                membershipRepository.findByCorporateGroupId(groupId);

        // return an empty list if the group does not contain any companies
        if (memberships.isEmpty()) {
            return List.of();
        }

        // extract the company ids from the memberships
        // stream goes through each membership and gets its counterparty id
        List<Long> counterpartyIds = memberships.stream()
                .map(membership -> membership.getCounterparty().getId())
                .toList();

        // only retrieve companies that have credit limits with the logged-in bank
        // this prevents the risk officer from assessing another bank's companies
        List<CreditLimit> authorizedLimits =
                creditLimitRepository.findByFinancialInstitutionIdAndCounterpartyIdIn(
                        institutionId,
                        counterpartyIds
                );

        // use a map to store each authorized company's id and name
        // key = company id
        // value = company name
        Map<Long, String> authorizedCompanies = new HashMap<>();

        // loop through the authorized credit limits
        for (CreditLimit limit : authorizedLimits) {

            // save each company's id and name in the map
            authorizedCompanies.put(
                    limit.getCounterparty().getId(),
                    limit.getCounterparty().getName()
            );
        }

        // return an empty list if the bank has no authorized companies in this group
        if (authorizedCompanies.isEmpty()) {
            return List.of();
        }


        //---------------------------------------------------------------------
        // threadpool and multithreading
        //---------------------------------------------------------------------

        log.info("ML BATCH AUTHORIZED | Group ID={} | Institution ID={} | Companies={}",
                groupId, institutionId, authorizedCompanies.size());

        // executorservice manages a pool of worker threads
        // newfixedthreadpool(4) allows up to 4 prediction tasks to run at the same time
        // if there are more than 4 companies the extra tasks wait in a queue
        // when a thread finishes it takes another waiting task
        // this reduces waiting time compared with assessing companies one by one
        ExecutorService executor = Executors.newFixedThreadPool(4);

        // future represents the result of a task that may still be running
        // this list stores all submitted tasks so we can collect their results later
        List<Future<CompanyRiskAssessmentResponse>> futures = new ArrayList<>();

        try {

            // loop through every authorized company and create a prediction task
            for (Map.Entry<Long, String> company : authorizedCompanies.entrySet()) {

                // get the company id from the map key
                Long counterpartyId = company.getKey();
                // get the company name from the map value
                String counterpartyName = company.getValue();

                // submit sends a task to the threadpool for execution
                // the lambda contains the code that a worker thread will execute
                // submit returns a future immediately without waiting for the prediction
                Future<CompanyRiskAssessmentResponse> future =
                        executor.submit(() -> {
                            long workerStart = System.nanoTime();
                            try {

                                // show which worker thread starts assessing this company
                                log.info("ML WORKER START | Group ID={} | Institution ID={} | Company ID={} | Company={} | Thread={}",
                                        groupId, institutionId, counterpartyId, counterpartyName, Thread.currentThread().getName());

                                // call the ml prediction service for this company
                                // authorization was already checked before submitting the task
                                // this method sends financial ratios to python fastapi
                                MlRiskPredictionResponse prediction =
                                        mlRiskPredictionService.predictAuthorizedRisk(
                                                counterpartyId
                                        );

                                // show which worker thread completed this company's assessment
                                log.info("ML WORKER COMPLETE | Group ID={} | Institution ID={} | Company ID={} | Company={} | Thread={} | Elapsed ms={}",
                                        groupId, institutionId, counterpartyId, counterpartyName, Thread.currentThread().getName(),
                                        (System.nanoTime() - workerStart) / 1_000_000.0);


                                // create a successful result containing the company's details
                                // the prediction includes the probability and risk level
                                return new CompanyRiskAssessmentResponse(
                                        counterpartyId,
                                        counterpartyName,
                                        prediction.getProbability(),
                                        prediction.getRiskLevel(),
                                        true,
                                        null
                                );

                            } catch (Exception exception) {
                                log.warn("ML WORKER FAILED | Group ID={} | Institution ID={} | Company ID={} | Company={} | Thread={} | Elapsed ms={}",
                                        groupId, institutionId, counterpartyId, counterpartyName, Thread.currentThread().getName(),
                                        (System.nanoTime() - workerStart) / 1_000_000.0);

                                // if this company's prediction fails return a failed result
                                // other worker threads can continue assessing their companies
                                // this prevents one failed prediction from stopping the whole group
                                return new CompanyRiskAssessmentResponse(
                                        counterpartyId,
                                        counterpartyName,
                                        null,
                                        null,
                                        false,
                                        "Risk assessment failed"
                                );
                            }
                        });

                // save the future so we can retrieve the result after submission
                futures.add(future);
            }

            //---------------------------------------------------------------------
            // collecting the results from the worker threads
            //---------------------------------------------------------------------

            // create a list to store the completed company assessments
            List<CompanyRiskAssessmentResponse> results = new ArrayList<>();

            // loop through the futures that were returned by submit
            for (Future<CompanyRiskAssessmentResponse> future : futures) {

                // future.get waits if the task has not finished yet
                // when the task finishes it returns the company assessment result
                // the tasks were already submitted so they can run concurrently
                results.add(future.get());
            }

            // return all completed assessments to the controller
            return results;

        } catch (InterruptedException exception) {

            // this happens if the waiting thread is interrupted
            // restore the interrupted status so java knows an interruption occurred
            Thread.currentThread().interrupt();

            // throw our custom exception for an interrupted assessment
            throw new ParallelRiskAssessmentException(
                    "Parallel risk assessment was interrupted",
                    exception
            );

        } catch (ExecutionException exception) {

            // this happens if a submitted task throws an uncaught exception
            // our worker normally catches individual prediction failures
            // this handles unexpected task failures
            throw new ParallelRiskAssessmentException(
                    "Unable to complete parallel risk assessment",
                    exception
            );

            // finally runs whether the assessment succeeds or fails
        } finally {

            // shutdownnow stops accepting tasks and attempts to interrupt remaining workers
            // this prevents the threadpool from continuing to run after the request finishes
            executor.shutdownNow();
        }
    }

}
