/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.inferred.freebuilder.processor;

import static org.inferred.freebuilder.processor.Util.erasesToAnyOf;
import static org.inferred.freebuilder.processor.Util.upperBound;
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeUnbox;
import static org.inferred.freebuilder.processor.util.feature.GuavaLibrary.GUAVA;
import static org.inferred.freebuilder.processor.util.feature.SourceLevel.SOURCE_LEVEL;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.PropertyCodeGenerator.Config;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.PreconditionExcerpts;
import org.inferred.freebuilder.processor.util.SourceBuilder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * {@link PropertyCodeGenerator.Factory} providing append-only semantics for {@link Map}
 * properties.
 */
public class MapPropertyFactory implements PropertyCodeGenerator.Factory {

  private static final String PUT_PREFIX = "put";
  private static final String PUT_ALL_PREFIX = "putAll";
  private static final String REMOVE_PREFIX = "remove";
  private static final String CLEAR_PREFIX = "clear";
  private static final String GET_PREFIX = "get";

  @Override
  public Optional<? extends PropertyCodeGenerator> create(Config config) {
    // No @Nullable properties
    if (!config.getProperty().getNullableAnnotations().isEmpty()) {
      return Optional.absent();
    }

    if (config.getProperty().getType().getKind() == TypeKind.DECLARED) {
      DeclaredType type = (DeclaredType) config.getProperty().getType();
      if (erasesToAnyOf(type, Map.class, ImmutableMap.class)) {
        TypeMirror keyType = upperBound(config.getElements(), type.getTypeArguments().get(0));
        TypeMirror valueType = upperBound(config.getElements(), type.getTypeArguments().get(1));
        Optional<TypeMirror> unboxedKeyType = maybeUnbox(keyType, config.getTypes());
        Optional<TypeMirror> unboxedValueType = maybeUnbox(valueType, config.getTypes());
        return Optional.of(new CodeGenerator(
            config.getProperty(), keyType, unboxedKeyType, valueType, unboxedValueType));
      }
    }
    return Optional.absent();
  }

  @VisibleForTesting
  static class CodeGenerator extends PropertyCodeGenerator {

    private final TypeMirror keyType;
    private final Optional<TypeMirror> unboxedKeyType;
    private final TypeMirror valueType;
    private final Optional<TypeMirror> unboxedValueType;

    CodeGenerator(
        Property property,
        TypeMirror keyType,
        Optional<TypeMirror> unboxedKeyType,
        TypeMirror valueType,
        Optional<TypeMirror> unboxedValueType) {
      super(property);
      this.keyType = keyType;
      this.unboxedKeyType = unboxedKeyType;
      this.valueType = valueType;
      this.unboxedValueType = unboxedValueType;
    }

    @Override
    public void addBuilderFieldDeclaration(SourceBuilder code) {
      code.add("private final %1$s<%2$s, %3$s> %4$s = new %1$s<",
          LinkedHashMap.class, keyType, valueType, property.getName());
      if (!code.feature(SOURCE_LEVEL).supportsDiamondOperator()) {
        code.add("%s, %s", keyType, valueType);
      }
      code.add(">();\n");
    }

    @Override
    public void addBuilderFieldAccessors(SourceBuilder code, Metadata metadata) {
      // put(K key, V value)
      code.addLine("")
          .addLine("/**")
          .addLine(" * Associates {@code key} with {@code value} in the map to be returned from")
          .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * Duplicate keys are not allowed.")
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
      if (!unboxedKeyType.isPresent() || !unboxedValueType.isPresent()) {
        code.add(" * @throws NullPointerException if ");
        if (unboxedKeyType.isPresent()) {
          code.add("{@code value} is");
        } else if (unboxedValueType.isPresent()) {
          code.add("{@code key} is");
        } else {
          code.add("either {@code key} or {@code value} are");
        }
        code.add(" null\n");
      }
      code.addLine(" * @throws IllegalArgumentException if {@code key} is already present")
          .addLine(" */")
          .addLine("public %s %s%s(%s key, %s value) {",
              metadata.getBuilder(),
              PUT_PREFIX,
              property.getCapitalizedName(),
              unboxedKeyType.or(keyType),
              unboxedValueType.or(valueType));
      if (!unboxedKeyType.isPresent()) {
        code.add(PreconditionExcerpts.checkNotNull("key"));
      }
      if (!unboxedValueType.isPresent()) {
        code.add(PreconditionExcerpts.checkNotNull("value"));
      }
      Excerpt keyNotPresent = new Excerpt() {
        @Override
        public void addTo(SourceBuilder source) {
          source.add("!%s.containsKey(key)", property.getName());
        }
      };
      code.add(PreconditionExcerpts.checkArgument(
              keyNotPresent, "Key already present in " + property.getName() + ": %s", "key"))
          .addLine("  %s.put(key, value);", property.getName())
          .addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");

      // putAll(Map<? extends K, ? extends V> map)
      code.addLine("")
          .addLine("/**")
          .addLine(" * Associates all of {@code map}'s keys and values in the map to be returned")
          .addLine(" * from %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * Duplicate keys are not allowed.")
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" * @throws NullPointerException if {@code map} is null or contains a")
          .addLine(" *     null key or value")
          .addLine(" * @throws IllegalArgumentException if any key is already present")
          .addLine(" */");
      addAccessorAnnotations(code);
      code.addLine("public %s %s%s(%s<? extends %s, ? extends %s> map) {",
              metadata.getBuilder(),
              PUT_ALL_PREFIX,
              property.getCapitalizedName(),
              Map.class,
              keyType,
              valueType)
          .addLine("  for (%s<? extends %s, ? extends %s> entry : map.entrySet()) {",
              Map.Entry.class, keyType, valueType)
          .addLine("    %s%s(entry.getKey(), entry.getValue());",
              PUT_PREFIX, property.getCapitalizedName())
          .addLine("  }")
          .addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");

      // remove(K key)
      code.addLine("")
          .addLine("/**")
          .addLine(" * Removes the mapping for {@code key} from the map to be returned from")
          .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());
      if (!unboxedKeyType.isPresent()) {
        code.addLine(" * @throws NullPointerException if {@code key} is null");
      }
      code.addLine(" * @throws IllegalArgumentException if {@code key} is not present")
          .addLine(" */")
          .addLine("public %s %s%s(%s key) {",
              metadata.getBuilder(),
              REMOVE_PREFIX,
              property.getCapitalizedName(),
              unboxedKeyType.or(keyType),
              valueType);
      if (!unboxedKeyType.isPresent()) {
        code.add(PreconditionExcerpts.checkNotNull("key"));
      }
      Excerpt keyPresent = new Excerpt() {
        @Override
        public void addTo(SourceBuilder source) {
          source.add("%s.containsKey(key)", property.getName());
        }
      };
      code.add(PreconditionExcerpts.checkArgument(
              keyPresent, "Key not present in " + property.getName() + ": %s", "key"))
          .addLine("  %s.remove(key);", property.getName())
          .addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");

      // clear()
      code.addLine("")
          .addLine("/**")
          .addLine(" * Removes all of the mappings from the map to be returned from ")
          .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" *")
          .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
          .addLine(" */")
          .addLine("public %s %s%s() {",
              metadata.getBuilder(),
              CLEAR_PREFIX,
              property.getCapitalizedName())
          .addLine("  %s.clear();", property.getName())
          .addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");

      // get()
      code.addLine("")
          .addLine("/**")
          .addLine(" * Returns an unmodifiable view of the map that will be returned by")
          .addLine(" * %s.", metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
          .addLine(" * Changes to this builder will be reflected in the view.")
          .addLine(" */")
          .addLine("public %s<%s, %s> %s%s() {",
              Map.class,
              keyType,
              valueType,
              GET_PREFIX,
              property.getCapitalizedName())
          .addLine("  return %s.unmodifiableMap(%s);", Collections.class, property.getName())
          .addLine("}");
    }

    @Override
    public void addFinalFieldAssignment(SourceBuilder code, String finalField, String builder) {
      code.add("%s = ", finalField);
      if (code.feature(GUAVA).isAvailable()) {
        code.add("%s.copyOf", ImmutableMap.class);
      } else {
        code.add("immutableMap");
      }
      code.add("(%s.%s);\n", builder, property.getName());
    }

    @Override
    public void addMergeFromValue(SourceBuilder code, String value) {
      code.addLine("%s%s(%s.%s());",
          PUT_ALL_PREFIX, property.getCapitalizedName(), value, property.getGetterName());
    }

    @Override
    public void addMergeFromBuilder(SourceBuilder code, Metadata metadata, String builder) {
      code.addLine("%s%s(((%s) %s).%s);",
          PUT_ALL_PREFIX,
          property.getCapitalizedName(),
          metadata.getGeneratedBuilder(),
          builder,
          property.getName());
    }

    @Override
    public void addSetFromResult(SourceBuilder code, String builder, String variable) {
      code.addLine("%s.%s%s(%s);",
          builder, PUT_ALL_PREFIX, property.getCapitalizedName(), variable);
    }

    @Override
    public boolean isTemplateRequiredInClear() {
      return false;
    }

    @Override
    public void addClear(SourceBuilder code, String template) {
      code.addLine("%s.clear();", property.getName());
    }

    @Override
    public void addPartialClear(SourceBuilder code) {
      code.addLine("%s.clear();", property.getName());
    }

    @Override
    public Set<StaticMethod> getStaticMethods() {
      return ImmutableSet.copyOf(StaticMethod.values());
    }
  }

  private enum StaticMethod implements Excerpt {
    IMMUTABLE_MAP() {
      @Override
      public void addTo(SourceBuilder code) {
        if (!code.feature(GUAVA).isAvailable()) {
          code.addLine("")
              .addLine("private static <K, V> %1$s<K, V> immutableMap(%1$s<K, V> entries) {",
                  Map.class)
              .addLine("  switch (entries.size()) {")
              .addLine("  case 0:")
              .addLine("    return %s.emptyMap();", Collections.class)
              .addLine("  case 1:")
              .addLine("    %s<K, V> entry = entries.entrySet().iterator().next();",
                  Map.Entry.class)
              .addLine("    return %s.singletonMap(entry.getKey(), entry.getValue());",
                  Collections.class)
              .addLine("  default:")
              .add("    return %s.unmodifiableMap(new %s<", Collections.class, LinkedHashMap.class);
          if (!code.feature(SOURCE_LEVEL).supportsDiamondOperator()) {
            code.add("K, V");
          }
          code.add(">(entries));\n")
              .addLine("  }")
              .addLine("}");
        }
      }
    }
  }
}
