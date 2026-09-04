package com.example.service;

import java.nio.file.Path;
import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ArchiveNameDetector {
    private static final Pattern SEVEN_ZIP_NUMBERED =
            Pattern.compile("^(.+)\\.7z\\.(\\d{3,})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ZIP_NUMBERED =
            Pattern.compile("^(.+)\\.zip\\.(\\d{3,})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ZIP_Z_NUMBERED =
            Pattern.compile("^(.+)\\.z(\\d{2,})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RAR_PART_NUMBERED =
            Pattern.compile("^(?:(.+)\\.)?part(\\d+)\\.rar$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RAR_OLD_NUMBERED =
            Pattern.compile("^(.+)\\.r(\\d{2,})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GENERIC_NUMBERED =
            Pattern.compile("^(.+)\\.(\\d{3,})$", Pattern.CASE_INSENSITIVE);

    public Map<Path, Detection> detect(Collection<Path> files) {
        Map<Path, Detection> result = new HashMap<>();
        Set<String> zipLegacyGroups = new HashSet<>();
        Set<String> rarLegacyGroups = new HashSet<>();
        Map<String, List<NumberedCandidate>> genericGroups = new HashMap<>();

        for (Path path : files) {
            Path normalized = path.toAbsolutePath().normalize();
            String name = normalized.getFileName().toString();
            Matcher matcher;

            matcher = SEVEN_ZIP_NUMBERED.matcher(name);
            if (matcher.matches()) {
                result.put(normalized, multipart("7Z 分卷", matcher.group(2)));
                continue;
            }

            matcher = ZIP_NUMBERED.matcher(name);
            if (matcher.matches()) {
                result.put(normalized, multipart("ZIP 数字分卷", matcher.group(2)));
                continue;
            }

            matcher = ZIP_Z_NUMBERED.matcher(name);
            if (matcher.matches()) {
                result.put(normalized, multipart("ZIP 分卷", matcher.group(2)));
                zipLegacyGroups.add(groupKey(normalized, matcher.group(1)));
                continue;
            }

            matcher = RAR_PART_NUMBERED.matcher(name);
            if (matcher.matches()) {
                result.put(normalized, multipart("RAR 分卷", matcher.group(2)));
                continue;
            }

            matcher = RAR_OLD_NUMBERED.matcher(name);
            if (matcher.matches()) {
                result.put(normalized, multipart("RAR 旧式分卷", matcher.group(2)));
                rarLegacyGroups.add(groupKey(normalized, matcher.group(1)));
                continue;
            }

            matcher = GENERIC_NUMBERED.matcher(name);
            if (matcher.matches()) {
                String number = matcher.group(2);
                String key = groupKey(normalized, matcher.group(1)) + "\u0000" + number.length();
                genericGroups.computeIfAbsent(key, ignored -> new ArrayList<>())
                        .add(new NumberedCandidate(normalized, matcher.group(1), number));
            }
        }

        markGenericNumberedGroups(genericGroups, result);

        for (Path path : files) {
            Path normalized = path.toAbsolutePath().normalize();
            if (result.containsKey(normalized)) {
                continue;
            }

            String name = normalized.getFileName().toString();
            String extension = ExtensionService.extensionOf(name);
            if (extension.equals("zip")) {
                String base = name.substring(0, name.length() - 4);
                if (zipLegacyGroups.contains(groupKey(normalized, base))) {
                    result.put(normalized, new Detection(true, true, "ZIP 分卷（末卷）", null, null));
                } else {
                    result.put(normalized, standard("ZIP"));
                }
            } else if (extension.equals("rar")) {
                String base = name.substring(0, name.length() - 4);
                if (rarLegacyGroups.contains(groupKey(normalized, base))) {
                    result.put(normalized, new Detection(true, true, "RAR 旧式分卷（首卷）", null, null));
                } else {
                    result.put(normalized, standard("RAR"));
                }
            } else if (extension.equals("7z")) {
                result.put(normalized, standard("7Z"));
            } else {
                result.put(normalized, new Detection(false, false, "非标准压缩后缀", null, null));
            }
        }

        return result;
    }

    private Detection multipart(String type, String number) {
        return new Detection(true, true, type + "（第 " + number + " 卷）", null, null);
    }

    private Detection standard(String type) {
        return new Detection(true, false, "普通 " + type + " 压缩包", null, null);
    }

    private void markGenericNumberedGroups(Map<String, List<NumberedCandidate>> groups,
                                           Map<Path, Detection> result) {
        for (List<NumberedCandidate> candidates : groups.values()) {
            Set<Integer> numbers = new HashSet<>();
            for (NumberedCandidate candidate : candidates) {
                try {
                    numbers.add(Integer.parseInt(candidate.number()));
                } catch (NumberFormatException ignored) {
                    // 超大编号不参与自动分卷判断。
                }
            }
            if (numbers.size() < 2) {
                continue;
            }
            for (NumberedCandidate candidate : candidates) {
                result.put(candidate.path(), new Detection(false, true,
                        "待恢复数字分卷（第 " + candidate.number() + " 卷）",
                        candidate.baseName(), candidate.number()));
            }
        }
    }

    private String groupKey(Path path, String baseName) {
        Path parent = path.getParent();
        String directory = parent == null ? "" : parent.toString();
        return (directory + "\u0000" + baseName).toLowerCase(Locale.ROOT);
    }

    public record Detection(boolean protectedName, boolean multipart, String description,
                            String volumeBaseName, String volumeNumber) {
        public Path targetPath(Path source, String targetExtension) {
            if (protectedName || volumeBaseName == null || volumeNumber == null) {
                return source;
            }
            String targetName = switch (targetExtension.toLowerCase(Locale.ROOT)) {
                case "7z" -> volumeBaseName + ".7z." + volumeNumber;
                case "zip" -> volumeBaseName + ".zip." + volumeNumber;
                case "rar" -> volumeBaseName + ".part" + volumeNumber + ".rar";
                default -> throw new IllegalArgumentException("目标后缀只能是 zip、7z 或 rar");
            };
            return source.resolveSibling(targetName);
        }
    }

    private record NumberedCandidate(Path path, String baseName, String number) {
    }
}
