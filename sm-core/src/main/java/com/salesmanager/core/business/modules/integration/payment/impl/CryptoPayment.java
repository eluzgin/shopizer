package com.salesmanager.core.business.modules.integration.payment.impl;

import com.braintreegateway.Environment;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.order.Order;
import com.salesmanager.core.model.payments.Payment;
import com.salesmanager.core.model.payments.PaymentType;
import com.salesmanager.core.model.payments.Transaction;
import com.salesmanager.core.model.payments.TransactionType;
import com.salesmanager.core.model.shoppingcart.ShoppingCartItem;
import com.salesmanager.core.model.system.IntegrationConfiguration;
import com.salesmanager.core.model.system.IntegrationModule;
import com.salesmanager.core.modules.integration.IntegrationException;
import com.salesmanager.core.modules.integration.payment.model.PaymentModule;
import org.apache.commons.lang.Validate;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.client.HttpClients;
import org.brunocvcunha.coinpayments.CoinPayments;
import org.brunocvcunha.coinpayments.model.BasicInfoResponse;
import org.brunocvcunha.coinpayments.model.CreateTransactionResponse;
import org.brunocvcunha.coinpayments.model.ResponseWrapper;
import org.brunocvcunha.coinpayments.model.TransactionDetailsResponse;
import org.brunocvcunha.coinpayments.requests.CoinPaymentsBasicAccountInfoRequest;
import org.brunocvcunha.coinpayments.requests.CoinPaymentsCreateTransactionRequest;
import org.brunocvcunha.coinpayments.requests.CoinPaymentsGetTransactionInfoRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * CryptoPayment integrates with CoinPayments API
 * https://www.coinpayments.net/apidoc
 * @author Eugene Luzgin <eugene@eostribe.io>
 */
public class CryptoPayment implements PaymentModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(CryptoPayment.class);

    @Override
    public void validateModuleConfiguration(IntegrationConfiguration integrationConfiguration, MerchantStore store) throws IntegrationException {
        List<String> errorFields = null;

        Map<String,String> keys = integrationConfiguration.getIntegrationKeys();

        //validate integrationKeys['public_key']
        if(keys==null || StringUtils.isBlank(keys.get("public_key"))) {
            if(errorFields==null) {
                errorFields = new ArrayList<String>();
            }
            errorFields.add("public_key");
        }

        //validate integrationKeys['private_key']
        if(keys==null || StringUtils.isBlank(keys.get("private_key"))) {
            if(errorFields==null) {
                errorFields = new ArrayList<String>();
            }
            errorFields.add("private_key");
        }

        if(errorFields!=null) {
            IntegrationException ex = new IntegrationException(IntegrationException.ERROR_VALIDATION_SAVE);
            ex.setErrorFields(errorFields);
            throw ex;
        }
    }

    @Override
    public Transaction initTransaction(MerchantStore store, Customer customer, BigDecimal amount, Payment payment, IntegrationConfiguration configuration, IntegrationModule module) throws IntegrationException {
        Validate.notNull(configuration,"Configuration cannot be null");

        String publicKey = configuration.getIntegrationKeys().get("public_key");
        String privateKey = configuration.getIntegrationKeys().get("private_key");

        Validate.notNull(publicKey,"public_key cannot be null");
        Validate.notNull(privateKey,"private_key cannot be null");

        Environment environment= Environment.PRODUCTION;
        if (configuration.getEnvironment().equals("TEST")) {// sandbox
            environment= Environment.SANDBOX;
        }

        Transaction transaction = new Transaction();
        transaction.setAmount(amount);
        transaction.setPaymentType(payment.getPaymentType());
        transaction.setTransactionDate(new Date());
        transaction.setTransactionType(payment.getTransactionType());
        try {
            CoinPayments api = CoinPayments.builder()
                    .publicKey(publicKey)
                    .privateKey(privateKey)
                    .client(HttpClients.createDefault()).build();
            ResponseWrapper<BasicInfoResponse> accountInfo = api.sendRequest(new CoinPaymentsBasicAccountInfoRequest());
            transaction.setDetails(environment.getEnvironmentName() + ": " + accountInfo.toString());
            return transaction;
        } catch (IOException ioex) {
            LOGGER.error("Error initializing CoinPayments API", ioex);
            throw new IntegrationException("Error intializing CoinPayments API", ioex);
        }
    }

    @Override
    public Transaction authorize(MerchantStore store, Customer customer, List<ShoppingCartItem> items, BigDecimal amount, Payment payment, IntegrationConfiguration configuration, IntegrationModule module) throws IntegrationException {
        Validate.notNull(configuration,"Configuration cannot be null");

        String publicKey = configuration.getIntegrationKeys().get("public_key");
        String privateKey = configuration.getIntegrationKeys().get("private_key");

        Validate.notNull(publicKey,"public_key cannot be null");
        Validate.notNull(privateKey,"private_key cannot be null");

        Environment environment= Environment.PRODUCTION;
        if (configuration.getEnvironment().equals("TEST")) {// sandbox
            environment= Environment.SANDBOX;
        }

        Transaction transaction = new Transaction();
        transaction.setAmount(amount);
        transaction.setPaymentType(payment.getPaymentType());
        transaction.setTransactionDate(new Date());
        transaction.setTransactionType(payment.getTransactionType());
        try {
            CoinPayments api = CoinPayments.builder()
                    .publicKey(publicKey)
                    .privateKey(privateKey)
                    .client(HttpClients.createDefault()).build();
            ResponseWrapper<CreateTransactionResponse> txResponse = api.sendRequest(
                    CoinPaymentsCreateTransactionRequest.builder()
                            .amount(amount.doubleValue())
                            .currencyPrice(payment.getCurrency().getCode())
                            .currencyTransfer("BTC")
                            .callbackUrl(store.getContinueshoppingurl())
                            .custom(customer.getEmailAddress())
                            .build());
            String txError = txResponse.getError();
            if(txError != null && txError.equalsIgnoreCase("OK")) {
                CreateTransactionResponse result = txResponse.getResult();
                transaction.setDetails(result.getTransactionId());
            } else {
                LOGGER.warn("Error in transaction "+txResponse.toString());
                transaction.setDetails("Error: "+txError);
            }
            return transaction;
        } catch (IOException ioex) {
            LOGGER.error("Error initializing CoinPayments API", ioex);
            throw new IntegrationException("Error initializing CoinPayments API", ioex);
        }
    }

    @Override
    public Transaction capture(MerchantStore store, Customer customer, Order order, Transaction capturableTransaction, IntegrationConfiguration configuration, IntegrationModule module) throws IntegrationException {
        Validate.notNull(configuration,"Configuration cannot be null");

        String publicKey = configuration.getIntegrationKeys().get("public_key");
        String privateKey = configuration.getIntegrationKeys().get("private_key");

        Validate.notNull(publicKey,"public_key cannot be null");
        Validate.notNull(privateKey,"private_key cannot be null");

        String details = capturableTransaction.getDetails();
        if(details == null || details.contains("Error")) {
            LOGGER.error(details);
            throw new IntegrationException("Failed to retrieve transaction id: "+details);
        }
        Transaction transaction = new Transaction();
        try {
            CoinPayments api = CoinPayments.builder()
                    .publicKey(publicKey)
                    .privateKey(privateKey)
                    .client(HttpClients.createDefault()).build();
            ResponseWrapper<TransactionDetailsResponse> txResponse = api.sendRequest(
                    CoinPaymentsGetTransactionInfoRequest.builder().txid(details)
                        .build());
            TransactionDetailsResponse txResult = txResponse.getResult();

            transaction.setAmount(order.getTotal());
            transaction.setOrder(order);
            transaction.setTransactionDate(new Date());
            transaction.setTransactionType(TransactionType.CAPTURE);
            transaction.setPaymentType(PaymentType.CRYPTOCURRENCY);
            transaction.getTransactionDetails().put("COIN", txResult.getCoin());
            transaction.getTransactionDetails().put("PAYMENT_ADDRESS", txResult.getPaymentAddress());
            transaction.getTransactionDetails().put("STATUS_TEXT", txResult.getStatusText());
            transaction.getTransactionDetails().put("TYPE", txResult.getType());
            transaction.getTransactionDetails().put("CONFIRMATIONS", String.valueOf(txResult.getConfirmations()));
            transaction.getTransactionDetails().put("AMOUNT_RECEIVED", String.valueOf(txResult.getReceivedf()));

            return transaction;
        } catch (IOException ioex) {
            LOGGER.error("Error initializing CoinPayments API", ioex);
            throw new IntegrationException("Error initializing CoinPayments API", ioex);
        }
    }

    @Override
    public Transaction authorizeAndCapture(MerchantStore store, Customer customer, List<ShoppingCartItem> items, BigDecimal amount, Payment payment, IntegrationConfiguration configuration, IntegrationModule module) throws IntegrationException {
        return null;
    }

    @Override
    public Transaction refund(boolean partial, MerchantStore store, Transaction transaction, Order order, BigDecimal amount, IntegrationConfiguration configuration, IntegrationModule module) throws IntegrationException {
        return null;
    }
}
