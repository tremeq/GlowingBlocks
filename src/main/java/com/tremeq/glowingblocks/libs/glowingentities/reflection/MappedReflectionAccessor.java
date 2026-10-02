package com.tremeq.glowingblocks.libs.glowingentities.reflection;

import com.tremeq.glowingblocks.libs.glowingentities.reflection.mappings.Mappings;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MappedReflectionAccessor implements ReflectionAccessor {
   @NotNull
   private final Map<String, MappedReflectionAccessor.ClassHandle> classes = new HashMap<>();

   public MappedReflectionAccessor(@NotNull Mappings mappings) {
      for (Mappings.ClassMapping classMapping : mappings.getClasses()) {
         this.classes.put(classMapping.getOriginalName(), new MappedReflectionAccessor.ClassHandle(classMapping));
      }
   }

   @NotNull
   public MappedReflectionAccessor.ClassHandle getClass(@NotNull String original) throws ClassNotFoundException {
      if (this.classes.containsKey(original)) {
         return this.classes.get(original);
      } else {
         throw new ClassNotFoundException(original);
      }
   }

   protected Class<?>[] getClassesFromMappingTypes(Type[] handles) throws ClassNotFoundException {
      Class<?>[] array = new Class[handles.length];

      for(int i = 0; i < handles.length; ++i) {
         Type handle = handles[i];
         Class<?> type;
         if (handle instanceof Class<?> clazz) {
            type = clazz;
         } else if (handle instanceof Mappings.ClassMapping mapping) {
            type = this.getClass(mapping.getOriginalName()).getClassInstance();
         } else if (handle instanceof MappedReflectionAccessor.ClassHandle classHandle) {
            type = classHandle.getClassInstance();
         } else if (handle instanceof Mappings.ClassMapping.ClassArrayType mappingArray) {
            type = this.getClassInstance(mappingArray.componentMapping().getTypeName()).arrayType();
         } else {
            throw new IllegalArgumentException(handle.getClass().toString());
         }
         array[i] = type;
      }

      return array;
   }

   private class ClassHandle implements ReflectionAccessor.ClassAccessor {
      @NotNull
      private final Mappings.ClassMapping mapping;
      private final List<MappedReflectionAccessor.ClassHandle.FieldHandle> fields;
      private final List<MappedReflectionAccessor.ClassHandle.MethodHandle> methods;
      @Nullable
      private Mappings.ClassMapping.ClassArrayType cachedArrayType;
      @Nullable
      private Class<?> cachedClass;

      public ClassHandle(@NotNull Mappings.ClassMapping mapping) {
         this.mapping = mapping;
         this.fields = mapping.getFields().stream().map(FieldHandle::new).toList();
         this.methods = mapping.getMethods().stream().map(MethodHandle::new).toList();
      }

      @NotNull
      public String getTypeName() {
         return this.mapping.getOriginalName();
      }

      @NotNull
      public Type getArrayType() {
         if (this.cachedArrayType == null) {
            this.cachedArrayType = this.mapping.getArrayType();
         }

         return this.cachedArrayType;
      }

      @NotNull
      public Class<?> getClassInstance() throws ClassNotFoundException {
         if (this.cachedClass == null) {
            this.cachedClass = Class.forName(this.mapping.getMappedName());
         }

         return this.cachedClass;
      }

      @NotNull
      public MappedReflectionAccessor.ClassHandle.FieldHandle getField(@NotNull String original) throws NoSuchFieldException {
         for (MappedReflectionAccessor.ClassHandle.FieldHandle field : this.fields) {
            if (field.mapping.getOriginalName().equals(original)) {
               return field;
            }
         }
         throw new NoSuchFieldException(original);
      }

      @NotNull
      public MappedReflectionAccessor.ClassHandle.MethodHandle getMethod(@NotNull String original, @NotNull Type... parameterTypes) throws NoSuchMethodException {
         for (MappedReflectionAccessor.ClassHandle.MethodHandle method : this.methods) {
            if (method.mapping.getOriginalName().equals(original) && method.mapping.isSameParameters(parameterTypes)) {
               return method;
            }
         }
         throw new NoSuchMethodException(Mappings.getStringForMethod(original, parameterTypes));
      }

      @NotNull
      public ReflectionAccessor.ClassAccessor.ConstructorAccessor getConstructor(@NotNull Type... parameterTypes) throws NoSuchMethodException, SecurityException, ClassNotFoundException {
         Constructor<?> constructor = this.getClassInstance().getDeclaredConstructor(MappedReflectionAccessor.this.getClassesFromMappingTypes(parameterTypes));
         return new TransparentReflectionAccessor.TransparentConstructor(constructor);
      }

      private class FieldHandle implements ReflectionAccessor.ClassAccessor.FieldAccessor {
         @NotNull
         private final Mappings.ClassMapping.FieldMapping mapping;
         @Nullable
         private Field cachedField;

         private FieldHandle(@NotNull Mappings.ClassMapping.FieldMapping mapping) {
            this.mapping = mapping;
         }

         @NotNull
         public Field getFieldInstance() throws NoSuchFieldException, SecurityException, ClassNotFoundException {
            if (this.cachedField == null) {
               this.cachedField = ClassHandle.this.getClassInstance().getDeclaredField(this.mapping.getMappedName());
               this.cachedField.setAccessible(true);
            }

            return this.cachedField;
         }

         public Object get(@Nullable Object instance) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException, ClassNotFoundException {
            return this.getFieldInstance().get(instance);
         }

         public void set(@Nullable Object instance, Object value) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException, ClassNotFoundException {
            this.getFieldInstance().set(instance, value);
         }
      }

      private class MethodHandle implements ReflectionAccessor.ClassAccessor.MethodAccessor {
         @NotNull
         private final Mappings.ClassMapping.MethodMapping mapping;
         @Nullable
         private Method cachedMethod;

         private MethodHandle(@NotNull Mappings.ClassMapping.MethodMapping mapping) {
            this.mapping = mapping;
         }

         @NotNull
         public Method getMethodInstance() throws NoSuchMethodException, SecurityException, ClassNotFoundException {
            if (this.cachedMethod == null) {
               this.cachedMethod = ClassHandle.this.getClassInstance().getDeclaredMethod(this.mapping.getMappedName(), MappedReflectionAccessor.this.getClassesFromMappingTypes(this.mapping.getParameterTypes()));
               this.cachedMethod.setAccessible(true);
            }

            return this.cachedMethod;
         }

         public Object invoke(@Nullable Object instance, @Nullable Object... args) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, ClassNotFoundException {
            return this.getMethodInstance().invoke(instance, args);
         }
      }
   }
}
