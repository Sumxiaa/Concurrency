package main;

import java.util.Arrays;

public record CommandLineOptions(
        boolean benchmarkMode,
        int initialThreads,
        int initialIterations,
        int initialWidth,
        int initialHeight,
        boolean smoothColoringEnabled,
        boolean antialiasingEnabled,
        int warmupRuns,
        int measuredRuns,
        int[] benchmarkThreadCounts
) {
    public static CommandLineOptions parse(String[] args) {
        int availableProcessors = Runtime.getRuntime().availableProcessors();

        boolean benchmarkMode = false;
        int initialThreads = availableProcessors;
        int initialIterations = MandelbrotExplorer.DEFAULT_ITERATIONS;
        int width = 800;
        int height = 600;
        boolean smooth = true;
        boolean antialiasing = false;
        int warmupRuns = 1;
        int measuredRuns = 5;
        String benchmarkThreadSpec = "1,4";

        for (int i = 0; i < args.length; i++) {
            String argument = args[i];
            String value = extractInlineValue(argument);
            String option = extractOptionName(argument);

            switch (option) {
                case "--help", "-h" -> {
                    printUsageAndExit();
                    return null;
                }
                case "--benchmark" -> benchmarkMode = true;
                case "--threads" -> {
                    String threadValue = readValue(args, i, value, option);
                    initialThreads = parsePositiveInt(firstThreadValue(threadValue), option);
                    benchmarkThreadSpec = threadValue;
                    if (value == null) {
                        i++;
                    }
                }
                case "--iterations" -> {
                    String iterationValue = readValue(args, i, value, option);
                    initialIterations = parsePositiveInt(iterationValue, option);
                    if (value == null) {
                        i++;
                    }
                }
                case "--width" -> {
                    String widthValue = readValue(args, i, value, option);
                    width = parsePositiveInt(widthValue, option);
                    if (value == null) {
                        i++;
                    }
                }
                case "--height" -> {
                    String heightValue = readValue(args, i, value, option);
                    height = parsePositiveInt(heightValue, option);
                    if (value == null) {
                        i++;
                    }
                }
                case "--smooth" -> {
                    String smoothValue = readValue(args, i, value, option);
                    smooth = parseBoolean(smoothValue, option);
                    if (value == null) {
                        i++;
                    }
                }
                case "--antialias" -> {
                    String antialiasValue = readValue(args, i, value, option);
                    antialiasing = parseBoolean(antialiasValue, option);
                    if (value == null) {
                        i++;
                    }
                }
                case "--warmup" -> {
                    String warmupValue = readValue(args, i, value, option);
                    warmupRuns = parseNonNegativeInt(warmupValue, option);
                    if (value == null) {
                        i++;
                    }
                }
                case "--runs" -> {
                    String runsValue = readValue(args, i, value, option);
                    measuredRuns = parsePositiveInt(runsValue, option);
                    if (value == null) {
                        i++;
                    }
                }
                default -> throw new IllegalArgumentException("Unknown option: " + argument);
            }
        }

        int[] benchmarkThreadCounts = parseThreadList(benchmarkThreadSpec, availableProcessors);
        if (benchmarkThreadCounts.length == 0) {
            benchmarkThreadCounts = new int[]{1, Math.min(4, availableProcessors)};
        }

        return new CommandLineOptions(
                benchmarkMode,
                initialThreads,
                initialIterations,
                width,
                height,
                smooth,
                antialiasing,
                warmupRuns,
                measuredRuns,
                benchmarkThreadCounts
        );
    }

    private static String extractOptionName(String argument) {
        int separatorIndex = argument.indexOf('=');
        return separatorIndex >= 0 ? argument.substring(0, separatorIndex) : argument;
    }

    private static String extractInlineValue(String argument) {
        int separatorIndex = argument.indexOf('=');
        return separatorIndex >= 0 ? argument.substring(separatorIndex + 1) : null;
    }

    private static String readValue(String[] args, int index, String inlineValue, String optionName) {
        if (inlineValue != null) {
            return inlineValue;
        }
        if (index + 1 >= args.length) {
            throw new IllegalArgumentException("Missing value for " + optionName);
        }
        return args[index + 1];
    }

    private static String firstThreadValue(String threadSpec) {
        String[] parts = threadSpec.split(",");
        return parts[0].trim();
    }

    private static int[] parseThreadList(String threadSpec, int availableProcessors) {
        return Arrays.stream(threadSpec.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .mapToInt(value -> clampThreadCount(parsePositiveInt(value, "--threads"), availableProcessors))
                .distinct()
                .sorted()
                .toArray();
    }

    private static int parsePositiveInt(String value, String optionName) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(optionName + " must be > 0.");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Could not parse " + optionName + " value: " + value, e);
        }
    }

    private static int parseNonNegativeInt(String value, String optionName) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw new IllegalArgumentException(optionName + " must be >= 0.");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Could not parse " + optionName + " value: " + value, e);
        }
    }

    private static boolean parseBoolean(String value, String optionName) {
        return switch (value.toLowerCase()) {
            case "on", "true", "yes", "1" -> true;
            case "off", "false", "no", "0" -> false;
            default -> throw new IllegalArgumentException("Could not parse " + optionName + " value: " + value);
        };
    }

    private static int clampThreadCount(int value, int availableProcessors) {
        return Math.max(1, Math.min(value, Math.max(1, availableProcessors * 2)));
    }

    private static void printUsageAndExit() {
        System.out.println("""
                Usage:
                  java -jar target/MiniProject-1.0-SNAPSHOT.jar
                  java -jar target/MiniProject-1.0-SNAPSHOT.jar --threads 8 --iterations 800
                  java -jar target/MiniProject-1.0-SNAPSHOT.jar --benchmark --threads 1,2,4,8 --iterations 1200 --width 1600 --height 1200 --runs 5 --warmup 1

                Options:
                  --benchmark            Run the headless speed benchmark instead of the GUI
                  --threads <list>       GUI: a single value, benchmark: a comma-separated list
                  --iterations <n>       Maximum iteration count
                  --width <n>            Window width or benchmark image width
                  --height <n>           Window height or benchmark image height
                  --smooth <on|off>      Enable or disable smooth colouring
                  --antialias <on|off>   Enable or disable 5-point antialiasing
                  --warmup <n>           Benchmark warmup runs per thread count
                  --runs <n>             Benchmark measured runs per thread count
                """);
        System.exit(0);
    }
}
