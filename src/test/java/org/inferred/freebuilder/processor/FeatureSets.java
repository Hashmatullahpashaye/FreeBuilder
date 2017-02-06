package org.inferred.freebuilder.processor;

import static org.inferred.freebuilder.processor.util.feature.SourceLevel.JAVA_6;
import static org.inferred.freebuilder.processor.util.feature.SourceLevel.JAVA_7;
import static org.inferred.freebuilder.processor.util.feature.SourceLevel.JAVA_8;

import com.google.common.collect.ImmutableList;

import org.inferred.freebuilder.processor.util.feature.FeatureSet;
import org.inferred.freebuilder.processor.util.feature.GuavaLibrary;
import org.inferred.freebuilder.processor.util.feature.StaticFeatureSet;

import java.util.List;

public final class FeatureSets {

  /** For tests valid in any environment. */
  public static final List<FeatureSet> ALL = ImmutableList.of(
      new StaticFeatureSet(JAVA_6),
      new StaticFeatureSet(JAVA_7),
      new StaticFeatureSet(JAVA_8),
      new StaticFeatureSet(JAVA_6, GuavaLibrary.AVAILABLE),
      new StaticFeatureSet(JAVA_7, GuavaLibrary.AVAILABLE),
      new StaticFeatureSet(JAVA_8, GuavaLibrary.AVAILABLE));

  /** For mapper and mutate method tests. */
  public static final List<FeatureSet> WITH_LAMBDAS = ImmutableList.of(
      new StaticFeatureSet(JAVA_8),
      new StaticFeatureSet(JAVA_8, GuavaLibrary.AVAILABLE));

  /** For tests using Guava types. */
  public static final List<FeatureSet> WITH_GUAVA = ImmutableList.of(
      new StaticFeatureSet(JAVA_6, GuavaLibrary.AVAILABLE),
      new StaticFeatureSet(JAVA_7, GuavaLibrary.AVAILABLE),
      new StaticFeatureSet(JAVA_8, GuavaLibrary.AVAILABLE));

  /** For mutate method tests using Guava types. */
  public static final List<FeatureSet> WITH_GUAVA_AND_LAMBDAS = ImmutableList.of(
      new StaticFeatureSet(JAVA_8, GuavaLibrary.AVAILABLE));

  private FeatureSets() {}
}
