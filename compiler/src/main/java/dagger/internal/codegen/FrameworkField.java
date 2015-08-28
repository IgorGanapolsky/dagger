/*
 * Copyright (C) 2014 Google, Inc.
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
package dagger.internal.codegen;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import dagger.MembersInjector;
import dagger.internal.codegen.ContributionBinding.BindingType;
import dagger.internal.codegen.writer.ClassName;
import dagger.internal.codegen.writer.ParameterizedTypeName;
import dagger.internal.codegen.writer.TypeName;
import dagger.internal.codegen.writer.TypeNames;
import dagger.producers.Producer;
import javax.inject.Provider;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementKindVisitor6;

/**
 * A value object that represents a field used by Dagger-generated code.
 *
 * @author Jesse Beder
 * @since 2.0
 */
@AutoValue
abstract class FrameworkField {
  // TODO(gak): reexamine the this class and how consistently we're using it and its creation
  // methods
  static FrameworkField createWithTypeFromKey(
      Class<?> frameworkClass, BindingKey bindingKey, String name) {
    String suffix = frameworkClass.getSimpleName();
    ParameterizedTypeName frameworkType = ParameterizedTypeName.create(
        ClassName.fromClass(frameworkClass),
        TypeNames.forTypeMirror(bindingKey.key().type()));
    return new AutoValue_FrameworkField(frameworkClass, frameworkType, bindingKey,
        name.endsWith(suffix) ? name : name + suffix);
  }

  static FrameworkField createForMapBindingContribution(
      Class<?> frameworkClass, BindingKey bindingKey, String name) {
    TypeMirror mapValueType =
        MoreTypes.asDeclared(bindingKey.key().type()).getTypeArguments().get(1);
    return new AutoValue_FrameworkField(frameworkClass,
        TypeNames.forTypeMirror(mapValueType),
        bindingKey,
        name);
  }

  static FrameworkField createForSyntheticContributionBinding(
      BindingKey bindingKey, int contributionNumber, ContributionBinding contributionBinding) {
    switch (contributionBinding.bindingType()) {
      case MAP:
        return createForMapBindingContribution(
            contributionBinding.frameworkClass(),
            BindingKey.create(bindingKey.kind(), contributionBinding.key()),
            KeyVariableNamer.INSTANCE.apply(bindingKey.key())
                + "Contribution" + contributionNumber);
      case SET:
        return createWithTypeFromKey(
            contributionBinding.frameworkClass(),
            bindingKey,
            KeyVariableNamer.INSTANCE.apply(bindingKey.key())
                + "Contribution" + contributionNumber);
      case UNIQUE:
        return createWithTypeFromKey(
            contributionBinding.frameworkClass(),
            bindingKey,
            KeyVariableNamer.INSTANCE.apply(bindingKey.key())
                + "Contribution" + contributionNumber);
      default:
        throw new AssertionError();
    }
  }

  static FrameworkField createForResolvedBindings(ResolvedBindings resolvedBindings) {
    BindingKey bindingKey = resolvedBindings.bindingKey();
    switch (bindingKey.kind()) {
      case CONTRIBUTION:
        ImmutableSet<? extends ContributionBinding> contributionBindings =
            resolvedBindings.contributionBindings();
        BindingType bindingsType = ProvisionBinding.bindingTypeFor(contributionBindings);
        switch (bindingsType) {
          case SET:
          case MAP:
            return createWithTypeFromKey(
                FrameworkField.frameworkClassForResolvedBindings(resolvedBindings),
                bindingKey,
                KeyVariableNamer.INSTANCE.apply(bindingKey.key()));
          case UNIQUE:
            ContributionBinding binding = Iterables.getOnlyElement(contributionBindings);
            return createWithTypeFromKey(
                FrameworkField.frameworkClassForResolvedBindings(resolvedBindings),
                bindingKey,
                BINDING_ELEMENT_NAME.visit(binding.bindingElement()));
          default:
            throw new AssertionError();
        }
      case MEMBERS_INJECTION:
        return createWithTypeFromKey(
            MembersInjector.class,
            bindingKey,
            CaseFormat.UPPER_CAMEL.to(
                CaseFormat.LOWER_CAMEL,
                Iterables.getOnlyElement(resolvedBindings.bindings())
                    .bindingElement()
                    .getSimpleName()
                    .toString()));
      default:
        throw new AssertionError();
    }
  }

  private static final ElementVisitor<String, Void> BINDING_ELEMENT_NAME =
      new ElementKindVisitor6<String, Void>() {
        @Override
        public String visitExecutableAsConstructor(ExecutableElement e, Void p) {
          return visit(e.getEnclosingElement());
        }

        @Override
        public String visitExecutableAsMethod(ExecutableElement e, Void p) {
          return e.getSimpleName().toString();
        }

        @Override
        public String visitType(TypeElement e, Void p) {
          return CaseFormat.UPPER_CAMEL.to(
              CaseFormat.LOWER_CAMEL, e.getSimpleName().toString());
        }
      };

  static Class<?> frameworkClassForResolvedBindings(ResolvedBindings resolvedBindings) {
    switch (resolvedBindings.bindingKey().kind()) {
      case CONTRIBUTION:
        for (ContributionBinding binding : resolvedBindings.contributionBindings()) {
          if (binding instanceof ProductionBinding) {
            return Producer.class;
          }
        }
        return Provider.class;
      case MEMBERS_INJECTION:
        return MembersInjector.class;
      default:
        throw new AssertionError();
    }
  }

  abstract Class<?> frameworkClass();
  abstract TypeName frameworkType();
  abstract BindingKey bindingKey();
  abstract String name();
}
