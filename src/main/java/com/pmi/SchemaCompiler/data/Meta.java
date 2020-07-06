package com.pmi.SchemaCompiler.data;

import java.text.MessageFormat;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Meta {
  private Boolean nullable;
  private Boolean serializeEnumAsInt;
  private String customValidator;

  public String toString() {
    return MessageFormat.format(
        "nullable: {0}, serializeEnumAsInt: {1}, customValidator: {2}",
        nullable, serializeEnumAsInt, customValidator);
  }
}
