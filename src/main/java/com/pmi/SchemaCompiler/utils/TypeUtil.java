package com.pmi.SchemaCompiler.utils;

import com.google.common.base.CaseFormat;
import com.pmi.SchemaCompiler.data.Enum;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TypeUtil {
  private static String regx = "\\[(.*)\\]";

  public static Set<String> immutableTypes = new HashSet<String>();

  static {
    immutableTypes.add("String");
    immutableTypes.add("Integer");
    immutableTypes.add("Boolean");
    immutableTypes.add("Float");
    immutableTypes.add("Double");
    immutableTypes.add("int");
    immutableTypes.add("boolean");
    immutableTypes.add("float");
    immutableTypes.add("double");
  }

  public static String parseGenericType(String type) {
    Pattern pattern = Pattern.compile(regx);
    Matcher matcher = pattern.matcher(type);

    if (!matcher.matches()) {
      return parseType(type);
    } else {
      String rest = matcher.group(1);
      String result = parseGenericType(rest);
      return MessageFormat.format("List<{0}>", result);
    }
  }

  public static String parseType(String type) {
    switch (type) {
      case "string":
        return "String";
      case "integer":
        return "Integer";
      case "Int":
        return "int";
      default:
        return type;
    }
  }

  public static String getterName(String fieldName, String fieldType) {
    if (fieldType.equals("boolean")) {
      return "is" + CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, fieldName);
    } else {
      return "get" + CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, fieldName);
    }
  }

  public static void addEnumTypes(List<Enum> enums) {
    for (Enum enumType : enums) {
      immutableTypes.add(enumType.getName());
    }
  }

  public static boolean isImmutableType(String type) {
    return immutableTypes.contains(type);
  }
}
