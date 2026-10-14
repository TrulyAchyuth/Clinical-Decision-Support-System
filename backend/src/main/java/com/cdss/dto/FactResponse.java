package com.cdss.dto;

import java.util.Arrays;
import java.util.List;

import com.cdss.ruleengine.FactDefinition;
import com.cdss.ruleengine.FactType;
import com.cdss.ruleengine.Operator;

/** A fact that rule conditions may use, and the operators allowed with it. */
public record FactResponse(String name, String label, String unit, FactType type, List<String> operators) {

	public static FactResponse from(FactDefinition fact) {
		List<String> operators = Arrays.stream(Operator.values())
			.filter((operator) -> operator.supports(fact.type()))
			.map(Operator::jsonName)
			.toList();
		return new FactResponse(fact.name(), fact.label(), fact.unit(), fact.type(), operators);
	}

}
