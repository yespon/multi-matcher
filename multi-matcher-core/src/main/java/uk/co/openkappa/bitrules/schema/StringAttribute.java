package uk.co.openkappa.bitrules.schema;

import uk.co.openkappa.bitrules.Mask;
import uk.co.openkappa.bitrules.ConstraintAccumulator;
import uk.co.openkappa.bitrules.masks.MaskFactory;
import uk.co.openkappa.bitrules.matchers.StringConstraintAccumulator;

import java.util.function.Function;

public class StringAttribute<Input> implements Attribute<Input> {

  private final Function<Input, String> accessor;

  public StringAttribute(Function<Input, String> accessor) {
    this.accessor = accessor;
  }

  @Override
  public <MaskType extends Mask<MaskType>> ConstraintAccumulator<Input, MaskType> toMatcher(MaskFactory<MaskType> maskFactory, int max) {
    return new StringConstraintAccumulator<>(accessor, maskFactory, max);
  }
}
