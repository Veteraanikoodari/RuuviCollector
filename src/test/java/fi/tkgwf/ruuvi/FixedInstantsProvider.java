package fi.tkgwf.ruuvi;

import java.util.List;
import java.util.function.Supplier;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value(staticConstructor = "from") public class FixedInstantsProvider implements Supplier<Long> {
  List<Long> instants;
  @NonFinal int $readCount = 0;

  @Override
  public Long get() {
    final long millis = instants.get($readCount);
    $readCount++;
    return millis;
  }
}
