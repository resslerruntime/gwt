/*
 * Copyright 2011 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.web.bindery.requestfactory.vm.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Provides access to payload deobfuscation services for server and JVM-based clients. The
 * deobfuscation data is baked into GWT-based clients by the generator.
 */
public class Deobfuscator {
  /**
   * Creates Deobfuscators.
   */
  public static class Builder {
    /**
     * Load a pre-computed Builder from the classpath. The builder implementation is expected to
     * have been generated by the annotation processor as part of the build process.
     * 
     * @see com.google.web.bindery.requestfactory.apt.DeobfuscatorBuilder
     * @see com.google.web.bindery.requestfactory.server.ResolverServiceLayer
     */
    public static Builder load(Class<?> clazz, ClassLoader resolveClassesWith) {
      Throwable ex;
      try {
        Class<?> found;
        try {
          // Used by the server
          found = Class.forName(clazz.getName() + GENERATED_SUFFIX, false, resolveClassesWith);
        } catch (ClassNotFoundException ignored) {
          // Used by JRE-only clients
          found = Class.forName(clazz.getName() + GENERATED_SUFFIX_LITE, false, resolveClassesWith);
        }
        Class<? extends Builder> builderClass = found.asSubclass(Builder.class);
        Builder builder = builderClass.newInstance();
        builder.resolveClassesWith = resolveClassesWith;
        return builder;
      } catch (ClassNotFoundException e) {
        throw new RuntimeException("The RequestFactory ValidationTool must be run for the "
            + clazz.getCanonicalName() + " RequestFactory type");
      } catch (InstantiationException e) {
        ex = e;
      } catch (IllegalAccessException e) {
        ex = e;
      }
      throw new RuntimeException(ex);
    }

    private ClassLoader resolveClassesWith;

    private Deobfuscator d = new Deobfuscator();

    {
      d.domainToClientType = new HashMap<String, List<String>>();
      d.operationData = new HashMap<OperationKey, OperationData>();
      d.typeTokens = new HashMap<String, String>();
    }

    public Deobfuscator build() {
      Deobfuscator toReturn = d;
      toReturn.domainToClientType = Collections.unmodifiableMap(toReturn.domainToClientType);
      toReturn.operationData = Collections.unmodifiableMap(toReturn.operationData);
      toReturn.referencedTypes =
          Collections.unmodifiableSet(new HashSet<String>(toReturn.typeTokens.values()));
      toReturn.typeTokens = Collections.unmodifiableMap(toReturn.typeTokens);
      d = null;
      return toReturn;
    }

    public Builder merge(Deobfuscator existing) {
      d.domainToClientType.putAll(merge(d.domainToClientType, existing.domainToClientType));
      d.operationData.putAll(existing.operationData);
      // referencedTypes recomputed in build()
      d.typeTokens.putAll(existing.typeTokens);
      return this;
    }

    public Builder withClientToDomainMappings(String domainBinaryName, List<String> value) {
      List<String> clientBinaryNames;
      switch (value.size()) {
        case 0:
          clientBinaryNames = Collections.emptyList();
          break;
        case 1:
          clientBinaryNames = Collections.singletonList(value.get(0));
          break;
        default:
          clientBinaryNames = Collections.unmodifiableList(new ArrayList<String>(value));
      }
      d.domainToClientType.put(domainBinaryName, clientBinaryNames);
      return this;
    }

    public Builder withOperation(OperationKey key, OperationData data) {
      d.operationData.put(key, data);
      return this;
    }

    public Builder withRawTypeToken(String token, String binaryName) {
      d.typeTokens.put(token, binaryName);
      return this;
    }

    /**
     * Merges two domainToClientType into one. Merged map's values are still ordering by
     * assignability, with most-derived types ordered first.
     * 
     * @see ClassComparator
     */
    private Map<String, List<String>> merge(Map<String, List<String>> domainToClientType1,
        Map<String, List<String>> domainToClientType2) {
      Map<String, List<String>> result = new HashMap<String, List<String>>();
      Set<String> domains = new HashSet<String>();
      domains.addAll(domainToClientType1.keySet());
      domains.addAll(domainToClientType2.keySet());
      for (String domain : domains) {
        List<String> clientTypes1 = domainToClientType1.get(domain);
        List<String> clientTypes2 = domainToClientType2.get(domain);
        List<String> clientTypes = mergeClientTypes(clientTypes1, clientTypes2);
        result.put(domain, clientTypes);
      }
      return result;
    }

    /**
     * Merges two clientType lists into one. Merged values are still ordering by assignability, with
     * most-derived types ordered first.
     * 
     * @see ClassComparator
     */
    private List<String> mergeClientTypes(List<String> clientTypes1, List<String> clientTypes2) {
      Set<String> clientTypes = new TreeSet<String>(new ClassComparator(resolveClassesWith));
      if (clientTypes1 != null) {
        clientTypes.addAll(clientTypes1);
      }
      if (clientTypes2 != null) {
        clientTypes.addAll(clientTypes2);
      }
      return Collections.unmodifiableList(new ArrayList<String>(clientTypes));
    }
  }

  private static final String GENERATED_SUFFIX = "DeobfuscatorBuilder";
  private static final String GENERATED_SUFFIX_LITE = GENERATED_SUFFIX + "Lite";

  /**
   * Maps domain types (e.g Foo) to client proxy types (e.g. FooAProxy, FooBProxy).
   */
  private Map<String, List<String>> domainToClientType;
  private Map<OperationKey, OperationData> operationData;
  private Set<String> referencedTypes;
  /**
   * Map of obfuscated ids to binary class names.
   */
  private Map<String, String> typeTokens;

  Deobfuscator() {
  }

  /**
   * Returns the client proxy types whose {@code @ProxyFor} is exactly {@code binaryTypeName}.
   * Ordered such that the most-derived types will be iterated over first.
   */
  public List<String> getClientProxies(String binaryTypeName) {
    return domainToClientType.get(binaryTypeName);
  }

  /**
   * Returns a method descriptor that should be invoked on the service object.
   */
  public String getDomainMethodDescriptor(String operation) {
    OperationData data = getData(operation);
    return data == null ? null : data.getDomainMethodDescriptor();
  }

  public String getRequestContext(String operation) {
    OperationData data = getData(operation);
    return data == null ? null : data.getRequestContext();
  }

  public String getRequestContextMethodDescriptor(String operation) {
    OperationData data = getData(operation);
    return data == null ? null : data.getClientMethodDescriptor();
  }

  public String getRequestContextMethodName(String operation) {
    OperationData data = getData(operation);
    return data == null ? null : data.getMethodName();
  }

  /**
   * Returns a type's binary name based on an obfuscated token.
   */
  public String getTypeFromToken(String token) {
    return typeTokens.get(token);
  }

  public boolean isReferencedType(String name) {
    return referencedTypes.contains(name);
  }

  private OperationData getData(String operation) {
    OperationData data = operationData.get(new OperationKey(operation));
    return data;
  }
}
