package com.cdss.ruleengine;

/** The kind of value a fact holds. */
public enum FactType {

	/** A single number, e.g. heart rate 88. */
	NUMBER,

	/** A set of codes, e.g. the patient's active ICD-10 codes. */
	CODE_LIST

}
