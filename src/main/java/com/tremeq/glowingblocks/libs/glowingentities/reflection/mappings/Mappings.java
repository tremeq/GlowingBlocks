package com.tremeq.glowingblocks.libs.glowingentities.reflection.mappings;

import com.tremeq.glowingblocks.libs.glowingentities.reflection.ReflectionAccessor;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public interface Mappings {
   Collection<? extends Mappings.ClassMapping> getClasses();

   @NotNull
   static String getStringForMethod(@NotNull String methodName, @NotNull Type... parameterTypes) {
      return methodName + (String)Stream.of(parameterTypes).map(Type::getTypeName).collect(Collectors.joining(", ", "(", ")"));
   }

   public interface ClassMapping extends Mappings.MappedObject, Type {
      @NotNull
      default String getTypeName() {
         return this.getOriginalName();
      }

      @NotNull
      default Mappings.ClassMapping.ClassArrayType getArrayType() {
         return new Mappings.ClassMapping.ClassArrayType(this);
      }

      Collection<? extends Mappings.ClassMapping.FieldMapping> getFields();

      Collection<? extends Mappings.ClassMapping.MethodMapping> getMethods();

      public static record ClassArrayType(@NotNull Type componentMapping) implements Type {
         public ClassArrayType(@NotNull Type componentMapping) {
            this.componentMapping = componentMapping;
         }

         @NotNull
         public String getTypeName() {
            return this.componentMapping().getTypeName() + "[]";
         }

         @NotNull
         public Type componentMapping() {
            return this.componentMapping;
         }
      }

      public interface MethodMapping extends Mappings.MappedObject {
         @NotNull
         Type[] getParameterTypes();

         default boolean isSameParameters(@NotNull Type[] types) {
            return ReflectionAccessor.areSameParameters(this.getParameterTypes(), types);
         }
      }

      public interface FieldMapping extends Mappings.MappedObject {
      }
   }

   public interface MappedObject {
      @NotNull
      String getOriginalName();

      @NotNull
      String getMappedName();
   }
}
