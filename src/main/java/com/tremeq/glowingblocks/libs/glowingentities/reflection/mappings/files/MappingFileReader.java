package com.tremeq.glowingblocks.libs.glowingentities.reflection.mappings.files;

import com.tremeq.glowingblocks.libs.glowingentities.reflection.Version;
import com.tremeq.glowingblocks.libs.glowingentities.reflection.mappings.Mappings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

public class MappingFileReader {
   private static final Pattern VERSION_PATTERN = Pattern.compile("# reflection-remapper \\| (?<major>\\d+)\\.(?<minor>\\d+)\\.(?<patch>\\d+)");
   @NotNull
   private final MappingType mappingType;
   @NotNull
   private final List<MappingFileReader.VersionPart> mappings;

   public MappingFileReader(@NotNull MappingType mappingType, @NotNull List<String> lines) throws IOException {
      this.mappingType = mappingType;
      this.mappings = readParts(lines);
   }

   public MappingFileReader(@NotNull MappingType mappingType, @NotNull List<String> lines, @NotNull Version version) throws IOException {
      this.mappingType = mappingType;
      this.mappings = List.of(new MappingFileReader.VersionPart(version, lines));
   }

   @NotNull
   public List<Version> getAvailableVersions() {
      return this.mappings.stream().map((x) -> {
         return x.version;
      }).sorted().toList();
   }

   public boolean keepOnlyVersion(@NotNull Version version) {
      if (!this.mappings.stream().anyMatch((part) -> {
         return part.version.equals(version);
      })) {
         return false;
      } else {
         this.mappings.removeIf((part) -> {
            return !part.version.equals(version);
         });
         return true;
      }
   }

   @NotNull
   public Optional<Version> keepBestMatchedVersion(@NotNull Version targetVersion) {
      Optional<Version> foundVersion = getBestMatchedVersion(targetVersion, this.getAvailableVersions());
      if (foundVersion.isPresent()) {
         this.keepOnlyVersion((Version)foundVersion.get());
      }

      return foundVersion;
   }

   public void parseMappings() {
      MappingFileReader.VersionPart version;
      for(Iterator var1 = this.mappings.iterator(); var1.hasNext(); version.mappings = this.mappingType.parse(version.lines)) {
         version = (MappingFileReader.VersionPart)var1.next();
      }

   }

   @NotNull
   public Mappings getParsedMappings(@NotNull Version version) {
      return ((MappingFileReader.VersionPart)this.mappings.stream().filter((x) -> {
         return x.version.equals(version);
      }).findAny().orElseThrow()).mappings;
   }

   @NotNull
   private static List<MappingFileReader.VersionPart> readParts(@NotNull List<String> lines) {
      List<MappingFileReader.VersionPart> parts = new ArrayList();
      MappingFileReader.VersionPart currentPart = null;
      Iterator var3 = lines.iterator();

      while(var3.hasNext()) {
         String line = (String)var3.next();
         Matcher versionMatcher = VERSION_PATTERN.matcher(line);
         if (versionMatcher.matches()) {
            if (currentPart != null) {
               parts.add(currentPart);
            }

            currentPart = new MappingFileReader.VersionPart(new Version(Integer.parseInt(versionMatcher.group("major")), Integer.parseInt(versionMatcher.group("minor")), Integer.parseInt(versionMatcher.group("patch"))), new ArrayList());
         } else if (!line.startsWith("#") && !line.isBlank()) {
            if (currentPart == null) {
               throw new IllegalArgumentException("File should start with a version information");
            }

            currentPart.lines.add(line);
         }
      }

      if (currentPart != null) {
         parts.add(currentPart);
      }

      return parts;
   }

   @NotNull
   public static Optional<Version> getBestMatchedVersion(@NotNull Version targetVersion, @NotNull List<Version> availableVersions) {
      Version lastVersion = null;
      Iterator var3 = availableVersions.iterator();

      while(true) {
         if (var3.hasNext()) {
            Version version = (Version)var3.next();
            if (version.is(targetVersion)) {
               return Optional.of(version);
            }

            if (version.isBefore(targetVersion)) {
               lastVersion = version;
            }

            if (!version.isAfter(targetVersion)) {
               continue;
            }
         }

         return Optional.ofNullable(lastVersion);
      }
   }

   private static class VersionPart {
      private final Version version;
      private final List<String> lines;
      private Mappings mappings;

      private VersionPart(Version version, List<String> lines) {
         this.version = version;
         this.lines = lines;
      }
   }
}
