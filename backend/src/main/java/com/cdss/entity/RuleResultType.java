package com.cdss.entity;

/** How one rule turned out in a decision. Rules that were evaluated and did not match are only counted. */
public enum RuleResultType {
	MATCHED,
	INSUFFICIENT_DATA,
	INVALID_RULE
}
