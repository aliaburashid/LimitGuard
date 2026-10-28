package com.example.limitguard.service;

import com.example.limitguard.dto.CounterpartyFinancialProfileResponse;
import com.example.limitguard.dto.MlRiskPredictionResponse;
import com.example.limitguard.repository.CreditLimitRepository;
import com.example.limitguard.security.InstitutionAccessService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import com.example.limitguard.exception.MlPredictionException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;


// Does two jobs:
// 1. Security: Checks whether the logged-in Risk Officer's bank has access to the company
// 2. ML prediction: Retrieves financial data, sends it to Python, and returns the result
@Service
public class MlRiskPredictionService {

    // used to get the company's saved financial information
    private final CounterpartyFinancialProfileService financialProfileService;

    // used to check if the company has a credit limit with the logged-in user's bank
    private final CreditLimitRepository creditLimitRepository;

    // used to find which bank the logged-in user belongs to
    private final InstitutionAccessService institutionAccessService;

    // used to send requests from java to the python ml service
    private final RestClient restClient;

    // constructor that receives the services needed for risk prediction
    public MlRiskPredictionService(
            CounterpartyFinancialProfileService financialProfileService,
            CreditLimitRepository creditLimitRepository,
            InstitutionAccessService institutionAccessService,
            @Value("${ml.service.url:http://127.0.0.1:8000}") String mlServiceUrl) {

        // save the services so we can use them in the methods below
        this.financialProfileService = financialProfileService;
        this.creditLimitRepository = creditLimitRepository;
        this.institutionAccessService = institutionAccessService;

        // create a simple http connection to the python service
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        // stop trying to connect if python does not respond within 3 seconds
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));

        // stop waiting if python takes more than 5 seconds to respond
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        // create a rest client using the python service address
        this.restClient = RestClient.builder()

                // the default python address is http://127.0.0.1:8000
                .baseUrl(mlServiceUrl)

                // use the timeouts we configured above
                .requestFactory(requestFactory)
                .build();
    }


    // predicts risk after checking the logged-in user's bank
    public MlRiskPredictionResponse predictRisk(Long counterpartyId) {

        // get the bank id of the currently logged-in user
        Long institutionId = institutionAccessService.getCurrentInstitutionId();

        // check if the company belongs to the logged-in user's bank
        boolean hasAccess =
                creditLimitRepository.existsByFinancialInstitutionIdAndCounterpartyId(
                        institutionId,
                        counterpartyId
                );

        // block access if the bank is not linked to this company
        if (!hasAccess) {
            throw new AccessDeniedException(
                    "You do not have permission to assess this counterparty"
            );
        }

        // run the ml prediction after checking permission
        return predictAuthorizedRisk(counterpartyId);
    }

    // runs the ml prediction for a company that has already passed the access check
    // this method does not check the logged-in user because it will also run inside worker threads
    MlRiskPredictionResponse predictAuthorizedRisk(Long counterpartyId) {

        // get the company's financial information saved in our database
        CounterpartyFinancialProfileResponse profile =
                financialProfileService.getProfile(counterpartyId);

        // create a map to hold the financial values we will send to python
        // the key is the financial field name and the value is its number
        Map<String, Object> requestBody = new LinkedHashMap<>();

        // add the company's financial values to the request
        requestBody.put("returnOnAssets", profile.getReturnOnAssets());
        requestBody.put("profitMargin", profile.getProfitMargin());
        requestBody.put("fixedAssetTurnover", profile.getFixedAssetTurnover());
        requestBody.put("debtToTotalCapitalPercent", profile.getDebtToTotalCapitalPercent());
        requestBody.put("interestCoverage", profile.getInterestCoverage());
        requestBody.put("totalDebtToEbitda", profile.getTotalDebtToEbitda());

        // try sending the financial information to python
        try {

            // send a post request to the python prediction endpoint
            MlRiskPredictionResponse prediction = restClient.post()

                    // call the /predict endpoint in fastapi
                    .uri("/predict")

                    // tell python that we are sending json data
                    .contentType(MediaType.APPLICATION_JSON)

                    // tell python that we want the response in json format
                    .accept(MediaType.APPLICATION_JSON)

                    // send the company's financial values in the request body
                    .body(requestBody)

                    // send the request and get the response from python
                    .retrieve()

                    // convert python's json response into our java response object
                    .body(MlRiskPredictionResponse.class);

            // check if python returned an empty response
            if (prediction == null) {
                throw new MlPredictionException(
                        "ML service returned an empty response"
                );
            }

            // return the prediction from the python model
            return prediction;

        } catch (RestClientException exception) {

            // print the error in the terminal for debugging
            exception.printStackTrace();

            // throw our custom exception 502 gateway when the python service fails
            throw new MlPredictionException(
                    "Unable to obtain ML risk prediction",
                    exception
            );
        }
    }



}
