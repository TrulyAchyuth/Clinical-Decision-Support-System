package com.cdss.entity;

import java.util.List;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Stores a list of strings in a MySQL JSON column, e.g. ["first", "second"]. */
@Converter
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {

	private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
	};

	@Override
	public String convertToDatabaseColumn(List<String> values) {
		return JsonMapper.shared().writeValueAsString(values == null ? List.of() : values);
	}

	@Override
	public List<String> convertToEntityAttribute(String json) {
		return (json == null) ? List.of() : JsonMapper.shared().readValue(json, STRING_LIST);
	}

}
