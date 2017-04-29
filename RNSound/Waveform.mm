
#import "Waveform.h"
#import "MeterTable.h"

@interface Waveform ()

@end

@implementation Waveform {
  MeterTable meterTable;
}

- (id)init {
  self = [super init];
  return self;
}

- (void)update
{
  int intensity = 0;
  if (_audioPlayer.playing )
  {
    [_audioPlayer updateMeters];
    
    float power = 0.0f;
    for (int i = 0; i < [_audioPlayer numberOfChannels]; i++) {
      power += [_audioPlayer averagePowerForChannel:i];
    }
    power /= [_audioPlayer numberOfChannels];
    
    float level = meterTable.ValueAt(power);
    intensity = level * 100;
    
    [self.delegate didUpdateWaveform:intensity];
  }
}

@end
