package com.openkappa.bitrules.nodes;

import com.openkappa.bitrules.LongRelation;
import org.roaringbitmap.ArrayContainer;
import org.roaringbitmap.Container;

import java.util.Arrays;

public class LongNode {

  private static final Container EMPTY = new ArrayContainer();

  private final LongRelation relation;

  private long[] thresholds = new long[16];
  private Container[] sets = new Container[16];
  private int count = 0;

  public LongNode(LongRelation relation) {
    this.relation = relation;
  }

  public void add(long value, short priority) {
    int position = Arrays.binarySearch(thresholds, 0, count, value);
    int insertionPoint = -(position + 1);
    if (position < 0 && insertionPoint < count) {
      incrementCount();
      for (int i = count; i > insertionPoint; --i) {
        sets[i] = sets[i - 1];
        thresholds[i] = thresholds[i - 1];
      }
      sets[insertionPoint] = new ArrayContainer().add(priority);
      thresholds[insertionPoint] = value;
    } else if (position < 0) {
      sets[count] = new ArrayContainer().add(priority);
      thresholds[count] = value;
      incrementCount();
    } else {
      sets[position] = sets[position].add(priority);
    }
  }

  public Container apply(long value, Container context) {
    switch (relation) {
      case GT:
        return context.iand(findRangeEncoded(value));
      case GE:
        return context.iand(findRangeEncodedInclusive(value));
      case LT:
        return context.iand(findReverseRangeEncoded(value));
      case LE:
        return context.iand(findReverseRangeEncodedInclusive(value));
      case EQ:
        return context.iand(findEqualityEncoded(value));
      default:
        return context;
    }
  }

  public LongNode optimise() {
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

  private Container findEqualityEncoded(long value) {
    int index = Arrays.binarySearch(thresholds, 0, count, value);
    return index >= 0 ? sets[index] : EMPTY;
  }

  private Container findRangeEncoded(long value) {
    int pos = Arrays.binarySearch(thresholds, 0, count, value);
    int index = (pos >= 0 ? pos : -(pos + 1)) - 1;
    return index >= 0 && index < count ? sets[index] : EMPTY;
  }

  private Container findRangeEncodedInclusive(long value) {
    int pos = Arrays.binarySearch(thresholds, 0, count, value);
    int index = (pos >= 0 ? pos : -(pos + 1) - 1);
    return index >= 0 && index < count ? sets[index] : EMPTY;
  }

  private Container findReverseRangeEncoded(long value) {
    int pos = Arrays.binarySearch(thresholds, 0, count, value);
    int index = (pos >= 0 ? pos + 1 : -(pos + 1));
    return index >= 0 && index < count ? sets[index] : EMPTY;
  }

  private Container findReverseRangeEncodedInclusive(long value) {
    int pos = Arrays.binarySearch(thresholds, 0, count, value);
    int index = (pos >= 0 ? pos : -(pos + 1));
    return index >= 0 && index < count ? sets[index] : EMPTY;
  }

  private void reverseRangeEncode() {
    for (int i = count - 2; i >= 0; --i) {
      sets[i] = sets[i].ior(sets[i + 1]);
    }
  }

  private void rangeEncode() {
    for (int i = 1; i < count; ++i) {
      sets[i] = sets[i].ior(sets[i - 1]);
    }
  }

  private void trim() {
    sets = Arrays.copyOf(sets, count);
    thresholds = Arrays.copyOf(thresholds, count);
  }

  private void incrementCount() {
    ++count;
    if (count == thresholds.length) {
      sets = Arrays.copyOf(sets, count * 2);
      thresholds = Arrays.copyOf(thresholds, count * 2);
    }
  }
}
