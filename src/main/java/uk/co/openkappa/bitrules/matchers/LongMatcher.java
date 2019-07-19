package uk.co.openkappa.bitrules.matchers;

import uk.co.openkappa.bitrules.*;
import uk.co.openkappa.bitrules.masks.MaskFactory;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.ToLongFunction;

import static uk.co.openkappa.bitrules.matchers.SelectivityHeuristics.avgCardinality;

public class LongMatcher<T, MaskType extends Mask<MaskType>> implements MutableMatcher<T, MaskType> {

  private final ToLongFunction<T> accessor;
  private final Map<Operation, LongNode<MaskType>> children = new EnumMap<>(Operation.class);
  private final MaskType empty;
  private final MaskType wildcards;

  public LongMatcher(ToLongFunction<T> accessor, MaskFactory<MaskType> maskFactory, int max) {
    this.accessor = accessor;
    this.empty = maskFactory.emptySingleton();
    this.wildcards = maskFactory.contiguous(max);
  }

  @Override
  public MaskType match(T value, MaskType context) {
    MaskType result = match(accessor.applyAsLong(value), context);
    return context.inPlaceAnd(result.inPlaceOr(wildcards));
  }

  @Override
  public void addConstraint(Constraint constraint, int priority) {
    Number number = constraint.getValue();
    long value = number.longValue();
    add(constraint.getOperation(), value, priority);
    wildcards.remove(priority);
  }

  @Override
  public Matcher<T, MaskType> freeze() {
    optimise();
    wildcards.optimise();
    return this;
  }


  private void add(Operation relation, long threshold, int priority) {
    children.computeIfAbsent(relation, r -> new LongNode<>(r, empty)).add(threshold, priority);
  }

  private MaskType match(long value, MaskType result) {
    MaskType temp = empty.clone();
    for (LongNode<MaskType> component : children.values()) {
      temp = temp.inPlaceOr(component.apply(value, result.clone()));
    }
    return result.inPlaceAnd(temp);
  }

  private void optimise() {
    Map<Operation, LongNode<MaskType>> optimised = new EnumMap<>(Operation.class);
    children.forEach((op, node) -> optimised.put(op, node.optimise()));
    children.putAll(optimised);
  }

  @Override
  public float averageSelectivity() {
    return avgCardinality(children.values(), LongNode::averageSelectivity);
  }

  public static class LongNode<MaskType extends Mask<MaskType>> {


    private final Operation relation;
    private final MaskType empty;

    private long[] thresholds = new long[16];
    private MaskType[] sets;
    private int count = 0;

    public LongNode(Operation relation, MaskType empty) {
      this.relation = relation;
      this.empty = empty;
      this.sets = (MaskType[]) Array.newInstance(empty.getClass(), 16);
    }

    public void add(long value, int priority) {
      if (count > 0 && value > thresholds[count - 1]) {
        ensureCapacity();
        int position = count;
        MaskType mask = sets[position];
        if (null == mask) {
          mask = empty.clone();
        }
        mask.add(priority);
        thresholds[position] = value;
        sets[position] = mask;
        ++count;
      } else {
        int position = Arrays.binarySearch(thresholds, 0, count, value);
        int insertionPoint = -(position + 1);
        if (position < 0 && insertionPoint < count) {
          ensureCapacity();
          for (int i = count; i > insertionPoint; --i) {
            sets[i] = sets[i - 1];
            thresholds[i] = thresholds[i - 1];
          }
          sets[insertionPoint] = maskWith(priority);
          thresholds[insertionPoint] = value;
          ++count;
        } else if (position < 0) {
          ensureCapacity();
          sets[count] = maskWith(priority);
          thresholds[count] = value;
          ++count;
        } else {
          sets[position].add(priority);
        }
      }
    }

    public MaskType apply(long value, MaskType context) {
      switch (relation) {
        case GT:
          return context.inPlaceAnd(findRangeEncoded(value));
        case GE:
          return context.inPlaceAnd(findRangeEncodedInclusive(value));
        case LT:
          return context.inPlaceAnd(findReverseRangeEncoded(value));
        case LE:
          return context.inPlaceAnd(findReverseRangeEncodedInclusive(value));
        case EQ:
          return context.inPlaceAnd(findEqualityEncoded(value));
        default:
          return context;
      }
    }

    public LongNode<MaskType> optimise() {
      switch (relation) {
        case GE:
        case GT:
          rangeEncode();
          break;
        case LE:
        case LT:
          reverseRangeEncode();
          break;
        default:
      }
      trim();
      return this;
    }

    public float averageSelectivity() {
      return avgCardinality(sets);
    }

    private MaskType findEqualityEncoded(long value) {
      int index = Arrays.binarySearch(thresholds, 0, count, value);
      return index >= 0 ? sets[index] : empty;
    }

    private MaskType findRangeEncoded(long value) {
      int pos = Arrays.binarySearch(thresholds, 0, count, value);
      int index = (pos >= 0 ? pos : -(pos + 1)) - 1;
      return index >= 0 && index < count ? sets[index] : empty;
    }

    private MaskType findRangeEncodedInclusive(long value) {
      int pos = Arrays.binarySearch(thresholds, 0, count, value);
      int index = (pos >= 0 ? pos : -(pos + 1) - 1);
      return index >= 0 && index < count ? sets[index] : empty;
    }

    private MaskType findReverseRangeEncoded(long value) {
      int pos = Arrays.binarySearch(thresholds, 0, count, value);
      int index = (pos >= 0 ? pos + 1 : -(pos + 1));
      return index >= 0 && index < count ? sets[index] : empty;
    }

    private MaskType findReverseRangeEncodedInclusive(long value) {
      int pos = Arrays.binarySearch(thresholds, 0, count, value);
      int index = (pos >= 0 ? pos : -(pos + 1));
      return index < count ? sets[index] : empty;
    }

    private void reverseRangeEncode() {
      for (int i = count - 2; i >= 0; --i) {
        sets[i].inPlaceOr(sets[i + 1]).optimise();
      }
    }

    private void rangeEncode() {
      for (int i = 1; i < count; ++i) {
        sets[i].inPlaceOr(sets[i - 1]).optimise();
      }
    }

    private void trim() {
      sets = Arrays.copyOf(sets, count);
      thresholds = Arrays.copyOf(thresholds, count);
    }

    private void ensureCapacity() {
      int newCount = count + 1;
      if (newCount == thresholds.length) {
        sets = Arrays.copyOf(sets, newCount * 2);
        thresholds = Arrays.copyOf(thresholds, newCount * 2);
      }
    }

    private MaskType maskWith(int value) {
      MaskType mask = empty.clone();
      mask.add(value);
      return mask;
    }

    @Override
    public String toString() {
      return Nodes.toString(count, relation,
              Arrays.stream(thresholds).boxed().iterator(),
              Arrays.stream(sets).iterator());
    }
  }
}
