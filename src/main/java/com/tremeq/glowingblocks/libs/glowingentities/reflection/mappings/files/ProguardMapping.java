package com.tremeq.glowingblocks.libs.glowingentities.reflection.mappings.files;

import com.tremeq.glowingblocks.libs.glowingentities.reflection.mappings.Mappings;
import com.tremeq.glowingblocks.libs.glowingentities.reflection.mappings.RealMappings;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class ProguardMapping implements MappingType {
   private static final Map<String, Class<?>> PRIMITIVES;
   private static final Logger LOGGER;
   private static final Pattern CLASS_REGEX;
   private static final Pattern METHOD_REGEX;
   private static final Pattern FIELD_REGEX;
   private static final Pattern METHOD_PARAMETERS_REGEX;
   private final boolean failOnLineParse;

   public ProguardMapping(boolean failOnLineParse) {
      this.failOnLineParse = failOnLineParse;
   }

   public Mappings parse(@NotNull List<String> lines) {
      List<ProguardMapping.ObfuscatedClass> parsedClasses = new ArrayList();
      String classOriginal = null;
      String classObfuscated = null;
      List<ProguardMapping.ObfuscatedMethod> classMethods = null;
      List<ProguardMapping.ObfuscatedField> classFields = null;
      Iterator var7 = lines.iterator();

      while(var7.hasNext()) {
         String line = (String)var7.next();
         if (!line.startsWith("#") && !line.contains("package-info")) {
            Matcher classMatch;
            if ((classMatch = CLASS_REGEX.matcher(line)).matches()) {
               if (classOriginal != null) {
                  parsedClasses.add(new ProguardMapping.ObfuscatedClass(classOriginal, classObfuscated, classMethods, classFields));
               }

               classOriginal = classMatch.group("original");
               classObfuscated = classMatch.group("obfuscated");
               classMethods = new ArrayList();
               classFields = new ArrayList();
            } else {
               Matcher methodMatch;
               if ((methodMatch = METHOD_REGEX.matcher(line)).matches()) {
                  classMethods.add(new ProguardMapping.ObfuscatedMethod(methodMatch.group("original"), methodMatch.group("obfuscated"), methodMatch.group("parameters")));
               } else {
                  Matcher fieldMatch;
                  if ((fieldMatch = FIELD_REGEX.matcher(line)).matches()) {
                     classFields.add(new ProguardMapping.ObfuscatedField(fieldMatch.group("original"), fieldMatch.group("obfuscated")));
                  } else {
                     if (this.failOnLineParse) {
                        throw new IllegalArgumentException("Failed to parse line " + line);
                     }

                     LOGGER.log(Level.WARNING, "Failed to parse line {0}", line);
                  }
               }
            }
         }
      }

      if (classOriginal != null) {
         parsedClasses.add(new ProguardMapping.ObfuscatedClass(classOriginal, classObfuscated, classMethods, classFields));
      }

      LOGGER.log(Level.FINE, "Found {0} classes to remap", parsedClasses.size());
      HashMap<String, Type> fakeTypes = new HashMap();
      Map<String, RealMappings.RealClassMapping> classes = (Map)parsedClasses.stream().map((clazz) -> {
         return new RealMappings.RealClassMapping(clazz.original, clazz.obfuscated, new ArrayList(), new ArrayList());
      }).collect(Collectors.toMap(RealMappings.RealClassMapping::getOriginalName, Function.identity()));
      Iterator var14 = parsedClasses.iterator();

      while(var14.hasNext()) {
         ProguardMapping.ObfuscatedClass parsedClass = (ProguardMapping.ObfuscatedClass)var14.next();
         RealMappings.RealClassMapping classMapping = (RealMappings.RealClassMapping)classes.get(parsedClass.original);
         classMapping.fields().addAll(parsedClass.fields.stream().map((field) -> {
            return new RealMappings.RealClassMapping.RealFieldMapping(field.original, field.obfuscated);
         }).toList());
         classMapping.methods().addAll(parsedClass.methods.stream().map((method) -> {
            return new RealMappings.RealClassMapping.RealMethodMapping(method.original, method.obfuscated, this.parseParameters(method.parameters, fakeTypes, classes));
         }).toList());
      }

      return new RealMappings(classes.values());
   }

   @NotNull
   protected Type[] parseParameters(@NotNull String parameters, Map<String, Type> fakeTypes, Map<String, RealMappings.RealClassMapping> classes) {
      List<Type> types = new ArrayList(2);

      Type type;
      for(Matcher matcher = METHOD_PARAMETERS_REGEX.matcher(parameters); matcher.find(); types.add(type)) {
         String typeName = matcher.group(1);
         boolean isArray = matcher.group(2) != null;
         Class<?> clazz = null;
         type = (Type)classes.get(typeName);
         if (type == null) {
            clazz = (Class)PRIMITIVES.get(typeName);
            if (clazz == null) {
               try {
                  clazz = Class.forName(typeName);
               } catch (ClassNotFoundException var11) {
                  if (!fakeTypes.containsKey(typeName)) {
                     LOGGER.log(Level.FINER, "Cannot find class {0}", typeName);
                     fakeTypes.put(typeName, new ProguardMapping.FakeType(typeName));
                  }

                  type = (Type)fakeTypes.get(typeName);
               }
            }
         }

         if (clazz != null) {
            type = isArray ? clazz.arrayType() : clazz;
         } else if (isArray) {
            type = new Mappings.ClassMapping.ClassArrayType((Type)type);
         }
      }

      return (Type[])types.toArray((x$0) -> {
         return new Type[x$0];
      });
   }

   public void write(@NotNull BufferedWriter writer, @NotNull Mappings mappings) throws IOException {
      Iterator var3 = mappings.getClasses().iterator();

      while(var3.hasNext()) {
         Mappings.ClassMapping mappedClass = (Mappings.ClassMapping)var3.next();
         writer.append("%s -> %s:".formatted(new Object[]{mappedClass.getOriginalName(), mappedClass.getMappedName()}));
         writer.newLine();
         Iterator var5 = mappedClass.getFields().iterator();

         while(var5.hasNext()) {
            Mappings.ClassMapping.FieldMapping mappedField = (Mappings.ClassMapping.FieldMapping)var5.next();
            writer.append("    %s -> %s".formatted(new Object[]{mappedField.getOriginalName(), mappedField.getMappedName()}));
            writer.newLine();
         }

         var5 = mappedClass.getMethods().iterator();

         while(var5.hasNext()) {
            Mappings.ClassMapping.MethodMapping mappedMethod = (Mappings.ClassMapping.MethodMapping)var5.next();
            String parameters = (String)Stream.of(mappedMethod.getParameterTypes()).map((parameter) -> {
               return parameter.getTypeName();
            }).collect(Collectors.joining(","));
            writer.append("    %s(%s) -> %s".formatted(new Object[]{mappedMethod.getOriginalName(), parameters, mappedMethod.getMappedName()}));
            writer.newLine();
         }
      }

   }

   static {
      PRIMITIVES = Map.of("boolean", Boolean.TYPE, "byte", Byte.TYPE, "short", Short.TYPE, "int", Integer.TYPE, "long", Long.TYPE, "float", Float.TYPE, "double", Double.TYPE, "char", Character.TYPE);
      LOGGER = Logger.getLogger("ProguardMapping");
      CLASS_REGEX = Pattern.compile("(?<original>[\\w.$]+) -> (?<obfuscated>[\\w.$]+):");
      METHOD_REGEX = Pattern.compile("    (?:(?:\\d+:\\d+:)?[\\w.$\\[\\]]+ )?(?<original>[\\w<>$]+)\\((?<parameters>[\\w.$, \\[\\]]*)\\) -> (?<obfuscated>[\\w<>]+)");
      FIELD_REGEX = Pattern.compile("    (?:[\\w.$\\[\\]]+ )?(?<original>[\\w$]+) -> (?<obfuscated>\\w+)");
      METHOD_PARAMETERS_REGEX = Pattern.compile("([\\w.$]+)(\\[\\])?,?");
   }

   private static record ObfuscatedClass(String original, String obfuscated, List<ProguardMapping.ObfuscatedMethod> methods, List<ProguardMapping.ObfuscatedField> fields) {
      private ObfuscatedClass(String original, String obfuscated, List<ProguardMapping.ObfuscatedMethod> methods, List<ProguardMapping.ObfuscatedField> fields) {
         this.original = original;
         this.obfuscated = obfuscated;
         this.methods = methods;
         this.fields = fields;
      }

      public String original() {
         return this.original;
      }

      public String obfuscated() {
         return this.obfuscated;
      }

      public List<ProguardMapping.ObfuscatedMethod> methods() {
         return this.methods;
      }

      public List<ProguardMapping.ObfuscatedField> fields() {
         return this.fields;
      }
   }

   private static record ObfuscatedMethod(String original, String obfuscated, String parameters) {
      private ObfuscatedMethod(String original, String obfuscated, String parameters) {
         this.original = original;
         this.obfuscated = obfuscated;
         this.parameters = parameters;
      }

      public String original() {
         return this.original;
      }

      public String obfuscated() {
         return this.obfuscated;
      }

      public String parameters() {
         return this.parameters;
      }
   }

   private static record ObfuscatedField(String original, String obfuscated) {
      private ObfuscatedField(String original, String obfuscated) {
         this.original = original;
         this.obfuscated = obfuscated;
      }

      public String original() {
         return this.original;
      }

      public String obfuscated() {
         return this.obfuscated;
      }
   }

   protected static record FakeType(String name) implements Type {
      protected FakeType(String name) {
         this.name = name;
      }

      public String getTypeName() {
         return this.name;
      }

      public String name() {
         return this.name;
      }
   }
}
