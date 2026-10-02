package com.tremeq.glowingblocks.libs.glowingentities.reflection;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TransparentReflectionAccessor implements ReflectionAccessor {
   private final Map<String, TransparentReflectionAccessor.MappedClassTransparent> classes = new HashMap<>();

   @NotNull
   public ReflectionAccessor.ClassAccessor getClass(@NotNull String name) throws ClassNotFoundException {
      TransparentReflectionAccessor.MappedClassTransparent clazz = this.classes.get(name);
      if (clazz == null) {
         clazz = new TransparentReflectionAccessor.MappedClassTransparent(Class.forName(name));
         this.classes.put(name, clazz);
      }

      return clazz;
   }

   protected Class<?>[] getClassesFromUserTypes(Type[] handles) {
      Class<?>[] array = new Class[handles.length];

      for(int i = 0; i < handles.length; ++i) {
         Type handle = handles[i];
         Class<?> type;
         if (handle instanceof Class<?> clazz) {
            type = clazz;
         } else if (handle instanceof TransparentReflectionAccessor.MappedClassTransparent mapped) {
            type = mapped.getClassInstance();
         } else {
            throw new IllegalArgumentException("Unsupported type: " + handle.getClass().getName());
         }
         array[i] = type;
      }

      return array;
   }

   private class MappedClassTransparent implements ReflectionAccessor.ClassAccessor {
      @NotNull
      private final Class<?> clazz;
      private final List<TransparentReflectionAccessor.TransparentField> fields = new ArrayList<>();
      private final List<TransparentReflectionAccessor.TransparentMethod> methods = new ArrayList<>();

      protected MappedClassTransparent(@NotNull Class<?> clazz) {
         this.clazz = clazz;
      }

      @NotNull
      public String getTypeName() {
         return this.clazz.getTypeName();
      }

      @NotNull
      public Type getArrayType() {
         return this.clazz.arrayType();
      }

      @NotNull
      public Class<?> getClassInstance() {
         return this.clazz;
      }

      @NotNull
      public ReflectionAccessor.ClassAccessor.FieldAccessor getField(@NotNull String original) throws NoSuchFieldException {
         for (TransparentReflectionAccessor.TransparentField field : this.fields) {
            if (field.field.getName().equals(original)) {
               return field;
            }
         }
         TransparentReflectionAccessor.TransparentField fieldx = new TransparentReflectionAccessor.TransparentField(this.clazz.getDeclaredField(original));
         this.fields.add(fieldx);
         return fieldx;
      }

      @NotNull
      public ReflectionAccessor.ClassAccessor.MethodAccessor getMethod(@NotNull String original, @NotNull Type... parameterTypes) throws NoSuchMethodException {
         for (TransparentReflectionAccessor.TransparentMethod method : this.methods) {
            if (method.getMethodInstance().getName().equals(original) && ReflectionAccessor.areSameParameters(parameterTypes, method.getMethodInstance().getParameterTypes())) {
               return method;
            }
         }
         TransparentReflectionAccessor.TransparentMethod methodx = new TransparentReflectionAccessor.TransparentMethod(this.clazz.getDeclaredMethod(original, TransparentReflectionAccessor.this.getClassesFromUserTypes(parameterTypes)));
         this.methods.add(methodx);
         return methodx;
      }

      @NotNull
      public ReflectionAccessor.ClassAccessor.ConstructorAccessor getConstructor(@NotNull Type... parameterTypes) throws NoSuchMethodException, SecurityException {
         Constructor<?> constructor = this.clazz.getDeclaredConstructor(TransparentReflectionAccessor.this.getClassesFromUserTypes(parameterTypes));
         return new TransparentReflectionAccessor.TransparentConstructor(constructor);
      }
   }

   protected static class TransparentConstructor implements ReflectionAccessor.ClassAccessor.ConstructorAccessor {
      @NotNull
      private final Constructor<?> constructor;

      public TransparentConstructor(@NotNull Constructor<?> constructor) {
         this.constructor = constructor;
         constructor.setAccessible(true);
      }

      public Constructor<?> getConstructorInstance() {
         return this.constructor;
      }

      public Object newInstance(@Nullable Object... args) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
         return this.constructor.newInstance(args);
      }
   }

   protected static class TransparentMethod implements ReflectionAccessor.ClassAccessor.MethodAccessor {
      @NotNull
      private final Method method;

      public TransparentMethod(@NotNull Method method) {
         this.method = method;
         method.setAccessible(true);
      }

      public Method getMethodInstance() {
         return this.method;
      }

      public Object invoke(@Nullable Object instance, @Nullable Object... args) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
         return this.method.invoke(instance, args);
      }
   }

   protected static class TransparentField implements ReflectionAccessor.ClassAccessor.FieldAccessor {
      @NotNull
      private final Field field;

      public TransparentField(@NotNull Field field) {
         this.field = field;
         field.setAccessible(true);
      }

      public Field getFieldInstance() {
         return this.field;
      }

      public Object get(@Nullable Object instance) throws IllegalArgumentException, IllegalAccessException {
         return this.field.get(instance);
      }

      public void set(@Nullable Object instance, Object value) throws IllegalArgumentException, IllegalAccessException {
         this.field.set(instance, value);
      }
   }
}
