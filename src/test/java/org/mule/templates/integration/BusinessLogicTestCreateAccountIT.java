/**
 * Mule Anypoint Template
 * Copyright (c) MuleSoft, Inc.
 * All rights reserved.  http://www.mulesoft.com
 */

package org.mule.templates.integration;

import static org.mule.templates.builders.SfdcObjectBuilder.anAccount;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mule.MessageExchangePattern;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.processor.chain.SubflowInterceptingChainLifecycleWrapper;

import com.mulesoft.module.batch.BatchTestHelper;
import com.sforce.soap.partner.SaveResult;

/**
 * The objective of this class is to validate the correct behavior of the Mule
 * Template that make calls to external systems.
 * 
 * The test will invoke the batch process and afterwards check that the
 * opportunities had been correctly created and that the ones that should be
 * filtered are not in the destination sand box.
 * 
 * The test validates that an account will get sync as result of the
 * integration.
 * 
 * @author cesar.garcia
 */
public class BusinessLogicTestCreateAccountIT extends AbstractTemplateTestCase {

	private BatchTestHelper helper;
	private List<Map<String, Object>> createdOpportunities = new ArrayList<Map<String, Object>>();
	private List<Map<String, Object>> createdAccounts = new ArrayList<Map<String, Object>>();

	@BeforeClass
	public static void init() {
		System.setProperty("account.sync.policy", "syncAccount");
	}

	@AfterClass
	public static void shutDown() {
		System.clearProperty("account.sync.policy");
	}

	@Before
	public void setUp() throws Exception {
		helper = new BatchTestHelper(muleContext);

		checkOpportunityflow = getSubFlow("retrieveOpportunityFlow");
		checkOpportunityflow.initialise();

		checkAccountflow = getSubFlow("retrieveAccountFlow");
		checkAccountflow.initialise();

		createTestDataInSandBox();
	}

	@After
	public void tearDown() throws Exception {
		deleteTestDataFromSandBox();
	}

	@Test
	public void testMainFlow() throws Exception {
		runFlow("mainFlow");

		// Wait for the batch job executed by the poll flow to finish
		helper.awaitJobTermination(TIMEOUT_SEC * 1000, 500);
		helper.assertJobWasSuccessful();

		Assert.assertEquals("The opportunity should not have been sync", null, invokeRetrieveFlow(checkOpportunityflow, createdOpportunities.get(0)));

		Assert.assertEquals("The opportunity should not have been sync", null, invokeRetrieveFlow(checkOpportunityflow, createdOpportunities.get(1)));

		Map<String, Object> accountPayload = invokeRetrieveFlow(checkAccountflow, createdAccounts.get(0));
		Map<String, Object> contacPayload = invokeRetrieveFlow(checkOpportunityflow, createdOpportunities.get(2));
		Assert.assertEquals("The opportunity should have been sync", createdOpportunities.get(2).get("Name"), contacPayload.get("Name"));

		Assert.assertEquals("The opportunity should belong to a different account ", accountPayload.get("Id"), contacPayload.get("AccountId"));
	}

	private void createTestDataInSandBox() throws MuleException, Exception {
		createAccounts();
		createOpportunities();
	}

	@SuppressWarnings("unchecked")
	private void createAccounts() throws Exception {
		SubflowInterceptingChainLifecycleWrapper flow = getSubFlow("createAccountFlow");
		flow.initialise();
		createdAccounts.add(anAccount().with("Name", buildUniqueName(TEMPLATE_NAME, "ReferencedAccountTest")).with("BillingCity", "San Francisco").with("BillingCountry", "USA").with(
				"Phone", "123456789").with("Industry", "Education").with("NumberOfEmployees", 9000).build());

		MuleEvent event = flow.process(getTestEvent(createdAccounts, MessageExchangePattern.REQUEST_RESPONSE));
		List<SaveResult> results = (List<SaveResult>) event.getMessage().getPayload();
		for (int i = 0; i < results.size(); i++) {
			createdAccounts.get(i).put("Id", results.get(i).getId());
		}

		System.out.println("Results of data creation in sandbox" + createdAccounts.toString());
	}

	@SuppressWarnings("unchecked")
	private void createOpportunities() throws Exception {
		SubflowInterceptingChainLifecycleWrapper flow = getSubFlow("createOpportunityFlow");
		flow.initialise();

		// This opportunity should not be sync
		Map<String, Object> opportunity = createOpportunity("A", 0);
		opportunity.put("Amount", 300);
		createdOpportunities.add(opportunity);

		// This opportunity should not be sync
		opportunity = createOpportunity("A", 1);
		opportunity.put("Amount", 1000);
		createdOpportunities.add(opportunity);

		// This opportunity should BE sync with it's account
		opportunity = createOpportunity("A", 2);
		opportunity.put("Amount", 30000);
		opportunity.put("AccountId", createdAccounts.get(0).get("Id"));
		createdOpportunities.add(opportunity);

		MuleEvent event = flow.process(getTestEvent(createdOpportunities, MessageExchangePattern.REQUEST_RESPONSE));
		List<SaveResult> results = (List<SaveResult>) event.getMessage().getPayload();
		for (int i = 0; i < results.size(); i++) {
			createdOpportunities.get(i).put("Id", results.get(i).getId());
		}
	}

	private void deleteTestDataFromSandBox() throws MuleException, Exception {
		deleteTestOpportunityFromSandBox(createdOpportunities);
		deleteTestAccountFromSandBox(createdAccounts);
	}

}
