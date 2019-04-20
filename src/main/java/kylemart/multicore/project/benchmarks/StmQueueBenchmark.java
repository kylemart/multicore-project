package kylemart.multicore.project.benchmarks;

import kylemart.multicore.project.queues.KQueue;
import kylemart.multicore.project.queues.StmQueue;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public class StmQueueBenchmark {

    private static final int iterations = 10;

    private static final int capacity = 500_000;

    private static final int numOperations = 500_000;

    private static final int[] threadCounts = {1, 2, 4, 8};

    public static void main(String[] args) throws InterruptedException {
        Map<DistributionFrequency, Operation[]> frequencyMap = new HashMap<>();

        for (DistributionFrequency frequency : DistributionFrequency.values()) {
            Operation[] operations = new Operation[100];

            List<Operation> operationsBuilder = new ArrayList<>();
            operationsBuilder.addAll(Collections.nCopies(frequency.dequeueCount, Operation.DEQ));
            operationsBuilder.addAll(Collections.nCopies(frequency.enqueueCount, Operation.ENQ));
            operationsBuilder.toArray(operations);

            frequencyMap.put(frequency, operations);
        }

        for (DistributionFrequency distribution : frequencyMap.keySet()) {
            Operation[] operations = frequencyMap.get(distribution);

            for (int threadCount : threadCounts) {
                ArrayList<Long> durations = new ArrayList<>(iterations);

                for (int iteration = 0; iteration < iterations; iteration++) {
                    StmQueue<Integer> queue = new StmQueue<>(capacity);

                    List<Thread> threads = new ArrayList<>();
                    CountDownLatch finished = new CountDownLatch(threadCount);
                    for (int thread = 0; thread < threadCount; thread++) {
                        threads.add(new Thread(() -> {
                            Random random = new Random();
                            for (int operation = 0; operation < numOperations; operation++) {
                                int index = random.nextInt(operations.length);
                                switch (operations[index]) {
                                    case ENQ:
                                        queue.enqueue(1);
                                        break;
                                    case DEQ:
                                        queue.dequeue();
                                        break;
                                }
                            }
                            finished.countDown();
                        }));
                    }

                    Instant start = Instant.now();
                    for (Thread thread : threads) {
                        thread.start();
                    }
                    finished.await();
                    Instant end = Instant.now();

                    durations.add(Duration.between(start, end).toNanos());
                }

                double averageTime = durations.stream()
                        .mapToLong(Long::valueOf)
                        .average()
                        .orElseThrow();

                System.out.printf("d=%s \t tc=%d \t | t=%.0fns\n",
                        distribution.name(), threadCount, averageTime);
            }
            System.out.println("-------------------------------------------------");
        }
    }

    private enum Operation { ENQ, DEQ }

    private enum DistributionFrequency {

        ENQ_50_DEQ_50(50, 50),
        ENQ_25_DEQ_75(25, 75),
        ENQ_75_DEQ_25(75, 25);

        final int enqueueCount;
        final int dequeueCount;

        DistributionFrequency(int enqueueCount, int dequeueCount) {
            this.enqueueCount = enqueueCount;
            this.dequeueCount = dequeueCount;
        }
    }
}