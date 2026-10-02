package com.tremeq.glowingblocks.libs.glowingentities.reflection;

import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public record Version(int major, int minor, int patch) implements Comparable<Version> {
   public static final Version ZERO = new Version(0, 0, 0);

   public Version(int major, int minor, int patch) {
      this.major = major;
      this.minor = minor;
      this.patch = patch;
   }

   public boolean is(int major, int minor, int patch) {
      return this.major() == major && this.minor() == minor && this.patch() == patch;
   }

   public boolean is(@NotNull Version version) {
      return this.equals(version);
   }

   public boolean isAfter(int major, int minor, int patch) {
      if (this.major() > major) {
         return true;
      } else if (this.major() < major) {
         return false;
      } else if (this.minor() > minor) {
         return true;
      } else if (this.minor() < minor) {
         return false;
      } else {
         return this.patch() >= patch;
      }
   }

   public boolean isAfter(@NotNull Version version) {
      return this.isAfter(version.major, version.minor, version.patch);
   }

   public boolean isBefore(int major, int minor, int patch) {
      return !this.isAfter(major, minor, patch);
   }

   public boolean isBefore(@NotNull Version version) {
      return this.isBefore(version.major, version.minor, version.patch);
   }

   public int compareTo(Version o) {
      if (o.equals(this)) {
         return 0;
      } else {
         return this.isAfter(o) ? 1 : -1;
      }
   }

   @NotNull
   public final String toString() {
      return this.toString(false);
   }

   @NotNull
   public final String toString(boolean omitPatch) {
      return omitPatch && this.patch == 0 ? "%d.%d".formatted(new Object[]{this.major, this.minor}) : "%d.%d.%d".formatted(new Object[]{this.major, this.minor, this.patch});
   }

   @NotNull
   public static Version parse(@NotNull String string) throws IllegalArgumentException {
      String[] parts = string.split("\\.");
      if (parts.length >= 2 && parts.length <= 3) {
         int major = Integer.parseInt(parts[0]);
         int minor = Integer.parseInt(parts[1]);
         int patch = parts.length == 3 ? Integer.parseInt(parts[2]) : 0;
         return new Version(major, minor, patch);
      } else {
         throw new IllegalArgumentException("Malformed version: " + string);
      }
   }

   @NotNull
   public static Version[] parseArray(String... versions) {
      return (Version[])Stream.of(versions).map(Version::parse).toArray((x$0) -> {
         return new Version[x$0];
      });
   }

   public int major() {
      return this.major;
   }

   public int minor() {
      return this.minor;
   }

   public int patch() {
      return this.patch;
   }
}
