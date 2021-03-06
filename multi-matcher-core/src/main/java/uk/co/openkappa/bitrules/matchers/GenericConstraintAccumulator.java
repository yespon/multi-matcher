package uk.co.openkappa.bitrules.matchers;

import uk.co.openkappa.bitrules.*;
import uk.co.openkappa.bitrules.masks.MaskFactory;
import uk.co.openkappa.bitrules.matchers.nodes.EqualityNode;
import uk.co.openkappa.bitrules.matchers.nodes.InequalityNode;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class GenericConstraintAccumulator<T, U, MaskType extends Mask<MaskType>>
        implements ConstraintAccumulator<T, MaskType> {

  protected final Function<T, U> accessor;
  protected final Supplier<Map<U, MaskType>> mapSupplier;
  protected final EnumMap<Operation, MutableNode<U, MaskType>> nodes = new EnumMap<>(Operation.class);
  protected final MaskType wildcard;
  protected final MaskType empty;
  protected final int max;
  private final MaskFactory<MaskType> maskFactory;

  public GenericConstraintAccumulator(Supplier<Map<U, MaskType>> mapSupplier,
                                      Function<T, U> accessor,
                                      MaskFactory<MaskType> maskFactory,
                                      int max) {
    this.accessor = accessor;
    this.mapSupplier = mapSupplier;
    this.wildcard = maskFactory.contiguous(max);
    this.empty = maskFactory.emptySingleton();
    this.maskFactory = maskFactory;
    this.max = max;
  }

  @Override
  public boolean addConstraint(Constraint constraint, int priority) {
    switch (constraint.getOperation()) {
      case NE:
        ((InequalityNode<U, MaskType>)nodes
                .computeIfAbsent(constraint.getOperation(), op -> new InequalityNode<>(mapSupplier.get(), maskFactory.contiguous(max))))
                .add(constraint.getValue(), priority);
        return true;
      case EQ:
        ((EqualityNode<U, MaskType>)nodes
                .computeIfAbsent(constraint.getOperation(), op -> new EqualityNode<>(mapSupplier.get(), empty, maskFactory.contiguous(max))))
                .add(constraint.getValue(), priority);
        wildcard.remove(priority);
        return true;
      default:
        return false;
    }
  }

  @Override
  public Matcher<T, MaskType> freeze() {
    wildcard.optimise();
    EnumMap<Operation, ClassificationNode<U, MaskType>> frozen = new EnumMap<>(Operation.class);
    nodes.forEach((op, node) -> node.link(nodes));
    nodes.forEach((op, node) -> frozen.put(op, node.freeze()));
    return new GenericMatcher<>(accessor, frozen, wildcard);
  }

}
