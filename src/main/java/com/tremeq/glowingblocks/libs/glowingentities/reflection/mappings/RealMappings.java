package com.tremeq.glowingblocks.libs.glowingentities.reflection.mappings;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public record RealMappings(@NotNull Collection<RealMappings.RealClassMapping> classes) implements Mappings {
   public RealMappings(@NotNull Collection<RealMappings.RealClassMapping> classes) {
      this.classes = classes;
   }

   public Collection<? extends Mappings.ClassMapping> getClasses() {
      return this.classes;
   }

   @NotNull
   public Collection<RealMappings.RealClassMapping> classes() {
      return this.classes;
   }

   public static record RealClassMapping(@NotNull String original, @NotNull String mapped, @NotNull List<RealMappings.RealClassMapping.RealFieldMapping> fields, @NotNull List<RealMappings.RealClassMapping.RealMethodMapping> methods) implements Mappings.ClassMapping {
      public RealClassMapping(@NotNull String original, @NotNull String mapped, @NotNull List<RealMappings.RealClassMapping.RealFieldMapping> fields, @NotNull List<RealMappings.RealClassMapping.RealMethodMapping> methods) {
         this.original = original;
         this.mapped = mapped;
         this.fields = fields;
         this.methods = methods;
      }

      @NotNull
      public String getOriginalName() {
         return this.original;
      }

      @NotNull
      public String getMappedName() {
         return this.mapped;
      }

      public Collection<? extends Mappings.ClassMapping.FieldMapping> getFields() {
         return this.fields;
      }

      public Collection<? extends Mappings.ClassMapping.MethodMapping> getMethods() {
         return this.methods;
      }

      @NotNull
      public String original() {
         return this.original;
      }

      @NotNull
      public String mapped() {
         return this.mapped;
      }

      @NotNull
      public List<RealMappings.RealClassMapping.RealFieldMapping> fields() {
         return this.fields;
      }

      @NotNull
      public List<RealMappings.RealClassMapping.RealMethodMapping> methods() {
         return this.methods;
      }

      public static record RealMethodMapping(@NotNull String original, @NotNull String mapped, @NotNull Type[] parameterTypes) implements Mappings.ClassMapping.MethodMapping {
         public RealMethodMapping(@NotNull String original, @NotNull String mapped, @NotNull Type[] parameterTypes) {
            this.original = original;
            this.mapped = mapped;
            this.parameterTypes = parameterTypes;
         }

         @NotNull
         public String getOriginalName() {
            return this.original;
         }

         @NotNull
         public String getMappedName() {
            return this.mapped;
         }

         @NotNull
         public Type[] getParameterTypes() {
            return this.parameterTypes;
         }

         @NotNull
         public String original() {
            return this.original;
         }

         @NotNull
         public String mapped() {
            return this.mapped;
         }

         @NotNull
         public Type[] parameterTypes() {
            return this.parameterTypes;
         }
      }

      public static record RealFieldMapping(@NotNull String original, @NotNull String mapped) implements Mappings.ClassMapping.FieldMapping {
         public RealFieldMapping(@NotNull String original, @NotNull String mapped) {
            this.original = original;
            this.mapped = mapped;
         }

         @NotNull
         public String getOriginalName() {
            return this.original;
         }

         @NotNull
         public String getMappedName() {
            return this.mapped;
         }

         @NotNull
         public String original() {
            return this.original;
         }

         @NotNull
         public String mapped() {
            return this.mapped;
         }
      }
   }
}
