package uk.co.openkappa.bitrules.matchers;

import uk.co.openkappa.bitrules.*;
import uk.co.openkappa.bitrules.masks.MaskFactory;
import uk.co.openkappa.bitrules.matchers.nodes.DoubleNode;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import static uk.co.openkappa.bitrules.matchers.SelectivityHeuristics.avgCardinality;

public class DoubleMatcher<T, MaskType extends Mask<MaskType>> implements ConstraintAccumulator<T, MaskType>,
        Matcher<T, MaskType> {

  private final ToDoubleFunction<T> accessor;
  private final Map<Operation, DoubleNode<MaskType>> children = new EnumMap<>(Operation.class);
  private final MaskType empty;
  private final MaskType wildcards;

  public DoubleMatcher(ToDoubleFunction<T> accessor, MaskFactory<MaskType> maskFactory, int max) {
    this.accessor = accessor;
    this.empty = maskFactory.emptySingleton();
    this.wildcards = maskFactory.contiguous(max);
  }

  @Override
  public MaskType match(T value, MaskType context) {
    MaskType result = match(accessor.applyAsDouble(value), context);
    return context.inPlaceAnd(result.inPlaceOr(wildcards));
  }

  @Override
  public boolean addConstraint(Constraint constraint, int priority) {
    Number number = constraint.getValue();
    double value = number.doubleValue();
    add(constraint.getOperation(), value, priority);
    wildcards.remove(priority);
    return true;
  }

  @Override
  public Matcher<T, MaskType> freeze() {
    optimise();
    wildcards.optimise();
    return this;
  }

  private void add(Operation relation, double threshold, int priority) {
    children.computeIfAbsent(relation, r -> new DoubleNode<>(r, empty))
            .add(threshold, priority);
  }

  private MaskType match(double value, MaskType result) {
    MaskType temp = empty.clone();
    for (DoubleNode<MaskType> component : children.values()) {
      temp = temp.inPlaceOr(component.match(value, result.clone()));
    }
    return result.inPlaceAnd(temp);
  }

  private void optimise() {
    Map<Operation, DoubleNode<MaskType>> optimised = new EnumMap<>(Operation.class);
    children.forEach((op, node) -> optimised.put(op, node.optimise()));
    children.putAll(optimised);
  }

  @Override
  public float averageSelectivity() {
    return avgCardinality(children.values(), DoubleNode::averageSelectivity);
  }


}
