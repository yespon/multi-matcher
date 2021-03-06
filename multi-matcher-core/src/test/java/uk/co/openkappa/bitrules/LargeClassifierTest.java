package uk.co.openkappa.bitrules;

import org.junit.jupiter.api.Test;
import uk.co.openkappa.bitrules.schema.Schema;

import java.util.HashMap;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;


public class LargeClassifierTest {

  @Test
  public void testLargeClassifier() {
    Classifier<int[], String> classifier = ImmutableClassifier.
            <Integer, int[], String>builder(Schema.<Integer, int[]>create()
                    .withAttribute(0, extract(0))
                    .withAttribute(1, extract(1))
                    .withAttribute(2, extract(2))
                    .withAttribute(3, extract(3))
                    .withAttribute(4, extract(4))
            ).build(IntStream.range(0, 50000)
            .mapToObj(i ->
                    MatchingConstraint.<Integer, String>anonymous()
                            .eq(0, i)
                            .eq(1, i)
                            .eq(2, i)
                            .eq(3, i)
                            .eq(4, i)
                            .classification("SEGMENT" + i)
                            .build())

            .collect(toList())
    );
    int[] vector = new int[]{5, 5, 5, 5, 5};
    String classification = classifier.classification(vector).orElseThrow(RuntimeException::new);
    assertEquals(classification, "SEGMENT5");
  }


  private static ToIntFunction<int[]> extract(int feature) {
    return features -> features[feature];
  }


  @Test
  public void testLargeDiscreteClassifier() {
    Classifier<Map<String, Object>, String> classifier = ImmutableClassifier.
            <String, Map<String, Object>, String>builder(Schema.<String, Map<String, Object>>create()
              .withStringAttribute("attr1", (Map<String, Object> map) -> (String)map.get("attr1"))
              .withStringAttribute("attr2", (Map<String, Object> map) -> (String)map.get("attr2"))
              .withStringAttribute("attr3", (Map<String, Object> map) -> (String)map.get("attr3"))
              .withStringAttribute("attr4", (Map<String, Object> map) -> (String)map.get("attr4"))
              .withStringAttribute("attr5", (Map<String, Object> map) -> (String)map.get("attr5"))
              .withStringAttribute("attr6", (Map<String, Object> map) -> (String)map.get("attr6"))
            ).build(IntStream.range(0, 50000)
            .mapToObj(i -> MatchingConstraint.<String, String>anonymous()
                .eq("attr1", "value" + (i / 10000))
                .eq("attr2", "value" + (i / 1000))
                .eq("attr3", "value" + (i / 500))
                .eq("attr4", "value" + (i / 250))
                .eq("attr5", "value" + (i / 100))
                .eq("attr6", "value" + (i / 10))
                .classification("SEGMENT" + i).build()
            ).collect(toList()));

    Map<String, Object> msg = new HashMap<>();
    msg.put("attr1", "value0");
    msg.put("attr2", "value0");
    msg.put("attr3", "value0");
    msg.put("attr4", "value0");
    msg.put("attr5", "value0");
    msg.put("attr6", "value9");


    String classification = null;
    long start = System.nanoTime();
    for (int i = 0; i < 1_000_000; ++i) {
      classification = classifier.classification(msg).orElseThrow(RuntimeException::new);
    }
    long end = System.nanoTime();
    System.out.println(1_000_000 / ((end - start) / 1e6) + "ops/ms");
    assertEquals("SEGMENT90", classification);
  }
}

