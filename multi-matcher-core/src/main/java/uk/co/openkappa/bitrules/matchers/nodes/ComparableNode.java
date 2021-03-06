package uk.co.openkappa.bitrules.matchers.nodes;

import uk.co.openkappa.bitrules.Mask;
import uk.co.openkappa.bitrules.Operation;
import uk.co.openkappa.bitrules.matchers.ClassificationNode;
import uk.co.openkappa.bitrules.matchers.MutableNode;

import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static uk.co.openkappa.bitrules.matchers.SelectivityHeuristics.avgCardinality;

public class ComparableNode<T, MaskType extends Mask<MaskType>> implements MutableNode<T, MaskType>, ClassificationNode<T, MaskType> {

  private final MaskType empty;
  private final NavigableMap<T, MaskType> sets;
  private final Operation operation;

  public ComparableNode(Comparator<T> comparator, Operation operation, MaskType empty) {
    this.sets = new TreeMap<>(comparator);
    this.operation = operation;
    this.empty = empty;
  }

  public void add(T value, int priority) {
    sets.compute(value, (k, v) -> {
      if (v == null) {
        v = empty.clone();
      }
      v.add(priority);
      return v;
    });
  }

  @Override
  public MaskType match(T value) {
    switch (operation) {
      case GE:
      case EQ:
      case LE:
        return sets.getOrDefault(value, empty);
      case LT:
        Map.Entry<T, MaskType> higher = sets.higherEntry(value);
        return null == higher ? empty : higher.getValue();
      case GT:
        Map.Entry<T, MaskType> lower = sets.lowerEntry(value);
        return null == lower ? empty : lower.getValue();
      default:
        return empty;
    }
  }

  public ComparableNode<T, MaskType> freeze() {
    switch (operation) {
      case GE:
      case GT:
        rangeEncode();
        return this;
      case LE:
      case LT:
        reverseRangeEncode();
        return this;
      default:
        return this;
    }
  }

  public float averageSelectivity() {
    return avgCardinality(sets.values());
  }

  private void rangeEncode() {
    MaskType prev = null;
    for (Map.Entry<T, MaskType> set : sets.entrySet()) {
      if (prev != null) {
        sets.put(set.getKey(), set.getValue().inPlaceOr(prev));
      }
      prev = set.getValue();
    }
  }

  private void reverseRangeEncode() {
    MaskType prev = null;
    for (Map.Entry<T, MaskType> set : sets.descendingMap().entrySet()) {
      if (prev != null) {
        sets.put(set.getKey(), set.getValue().inPlaceOr(prev));
      }
      prev = set.getValue();
    }
  }

  @Override
  public String toString() {
    return Nodes.toString(sets.size(), operation, sets);
  }
}
