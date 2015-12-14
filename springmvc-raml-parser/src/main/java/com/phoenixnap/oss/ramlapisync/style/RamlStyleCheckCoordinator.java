package com.phoenixnap.oss.ramlapisync.style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.raml.model.Action;
import org.raml.model.ActionType;
import org.raml.model.Raml;
import org.raml.model.Resource;
import org.raml.model.parameter.QueryParameter;
import org.raml.model.parameter.UriParameter;

import com.phoenixnap.oss.ramlapisync.naming.Pair;
import com.phoenixnap.oss.ramlapisync.verification.Issue;
import com.phoenixnap.oss.ramlapisync.verification.IssueLocation;
import com.phoenixnap.oss.ramlapisync.verification.IssueSeverity;
import com.phoenixnap.oss.ramlapisync.verification.IssueType;
import com.phoenixnap.oss.ramlapisync.verification.RamlChecker;


public class RamlStyleCheckCoordinator implements RamlChecker {
	
	/**
	 * Boolean flag to enable style checking of code too. Sicne RAML and code should be in sync this could be kept off to improve performance
	 */
	private boolean ignoreCodeStyle = true;
	
	private List<Issue> warnings = new ArrayList<>();
	
	private List<RamlStyleChecker> checkers;
	
	public RamlStyleCheckCoordinator (List<RamlStyleChecker> styleChecks) {		
		checkers = new ArrayList<>();
		checkers.addAll(styleChecks);	
	}
	
	/**
	 * Performs a specific check across two Raml Models. 
	 * 
	 * @param published The Raml as published in the contract
	 * @param implemented The Raml as generated from the implementation
	 * @return A pair containing a list of Warnings and an empty list of Errors (as first and second respectively)
	 */
	public Pair<List<Issue>, List<Issue>> check (Raml published, Raml implemented) {
		
		checkChildren(published.getResources(), IssueLocation.CONTRACT);
		if (!ignoreCodeStyle) {
			checkChildren(implemented.getResources(), IssueLocation.SOURCE);
		}
		
		return new Pair<List<Issue>, List<Issue>>(warnings, Collections.emptyList());		
	}



	private void checkChildren(Map<String, Resource> resources, IssueLocation location) {
		if (resources != null) {
			for (Entry<String, Resource> entry : resources.entrySet()) {
				Resource resource = entry.getValue();
				for (RamlStyleChecker checker : checkers) {
					warnings.addAll(checker.checkResourceStyle(entry.getKey(), resource, location));
				}
				
				Map<String, UriParameter> uriParameters = resource.getUriParameters();
				if(uriParameters != null) {
					for (Entry<String, UriParameter> uriParameter : uriParameters.entrySet()) {
						for (RamlStyleChecker checker : checkers) {
							warnings.addAll(checker.checkParameterStyle(uriParameter.getKey(), uriParameter.getValue()));
						}
					}
				}
				
				Map<ActionType, Action> actions = resource.getActions();
				if (actions != null) {
					for (Entry<ActionType, Action> actionEntry : actions.entrySet()) {
						for (RamlStyleChecker checker : checkers) {
							warnings.addAll(checker.checkActionStyle(actionEntry.getKey(), actionEntry.getValue(), location));
						}
						
						/*
						 * If we have query parameters in this call check it 
						 */
						Map<String, QueryParameter> queryParameters = actionEntry.getValue().getQueryParameters();
						if(queryParameters != null) {
							for (Entry<String, QueryParameter> queryParam : queryParameters.entrySet()) {
								for (RamlStyleChecker checker : checkers) {
									warnings.addAll(checker.checkParameterStyle(queryParam.getKey(), queryParam.getValue()));
								}
							}
						}
						
						
					}
				}
				checkChildren(resource.getResources(), location);
			}
		}
	}
	
	
	
	protected final void addIssue(IssueLocation location, String description, String ramlLocation) {
		warnings.add(new Issue(IssueSeverity.WARNING, location, IssueType.STYLE, description, ramlLocation));
	}

}
