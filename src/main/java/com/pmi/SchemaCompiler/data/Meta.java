package com.pmi.SchemaCompiler.data;

import java.text.MessageFormat;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Meta {
  private Boolean nullable;
  private Boolean serializeEnumAsInt;
  private Boolean serializeIgnore;
  private String customValidator;

  public String toString() {
    return MessageFormat.format(
        "nullable: {0}, serializeEnumAsInt: {1}, serializeIgnore: {2}, customValidator: {3}",
        nullable, serializeEnumAsInt, serializeIgnore, customValidator);
  }
}
