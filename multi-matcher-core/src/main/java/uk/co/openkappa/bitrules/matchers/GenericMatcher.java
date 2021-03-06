package uk.co.openkappa.bitrules.matchers;

import uk.co.openkappa.bitrules.Mask;
import uk.co.openkappa.bitrules.Matcher;
import uk.co.openkappa.bitrules.Operation;

import java.util.EnumMap;
import java.util.function.Function;

import static uk.co.openkappa.bitrules.matchers.SelectivityHeuristics.avgCardinality;

class GenericMatcher<T, U, MaskType extends Mask<MaskType>> implements Matcher<T, MaskType> {

  private final Function<T, U> accessor;
  private final EnumMap<Operation, ClassificationNode<U, MaskType>> nodes;
  private final MaskType wildcard;

  GenericMatcher(Function<T, U> accessor,
                 EnumMap<Operation, ClassificationNode<U, MaskType>> nodes,
                 MaskType wildcard) {
    this.accessor = accessor;
    this.nodes = nodes;
    this.wildcard = wildcard;
  }

  @Override
  public MaskType match(T input, MaskType context) {
    U value = accessor.apply(input);
    MaskType result = context.clone();
    for (var component : nodes.entrySet()) {
      var op = component.getKey();
      var node = component.getValue();
      switch (op) {
        case NE:
          result = result.inPlaceAnd(node.match(value));
          break;
        default:
          result = result.inPlaceAnd(node.match(value)).inPlaceOr(wildcard);
      }
    }
    return context.inPlaceAnd(result);
  }

  @Override
  public float averageSelectivity() {
    return avgCardinality(nodes.values(), ClassificationNode::averageSelectivity);
  }
}
