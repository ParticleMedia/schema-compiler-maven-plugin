package com.pmi.SchemaCompiler.data;

import com.google.common.base.CaseFormat;
import com.pmi.SchemaCompiler.utils.TypeUtil;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Field {
  private String name;
  private String alias;
  private String type;
  private Meta meta;
  private String doc;

  // These fields do not appear in schema yaml file. They are only used as helper to generate source
  // codes.
  private String getterName;
  private String setterName;
  private boolean isList;
  private String clonerName;
  private boolean useClone;

  public void setType(String type) {
    this.type = TypeUtil.parseGenericType(type);
    this.getterName = TypeUtil.getterName(this.name, type);
    this.setterName = "set" + CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, this.name);

    if (this.type.startsWith("List")) {
      this.isList = true;
    } else {
      this.isList = false;
    }

    if (TypeUtil.isImmutableType(this.type)) {
      this.clonerName = this.getterName;
    } else {
      this.clonerName = this.name + ".clone";
    }

    switch (this.type) {
      case "String":
      case "Integer":
      case "Boolean":
      case "Double":
      case "Float":
      case "int":
      case "boolean":
      case "double":
      case "float":
      case "List<String>":
      case "List<Integer>":
      case "List<Boolean>":
      case "List<Double>":
      case "List<Float>":
        this.useClone = false;
        break;
      default:
        this.useClone = true;
        break;
    }
  }
}
