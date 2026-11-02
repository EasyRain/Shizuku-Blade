package com.buwan.shizuku.util.tooltip;

public class TimeSystem {
    private static TimeSystem instance;
    private long totalTicks = 0L;
    private float totalSeconds = 0.0F;
    private float smoothTime = 0.0F;
    private long startTimeMillis = System.currentTimeMillis();
    private float realTimeSeconds = 0.0F;
    private float tickRate = 20.0F;
    private float timeScale = 1.0F;
    private boolean isPaused = false;
    private boolean useRealTime = true;
    private static final float MAX_FLOAT_CYCLE = 3600.0F;
    private static final float MAX_SHADER_TIME = 86400.0F;
    private static final long MAX_TICK_CYCLE = 72000L;
    private final TimeOutput gameTime = new TimeOutput();
    private final TimeOutput shaderTime = new TimeOutput();
    private final TimeOutput animationTime = new TimeOutput();
    private final TimeOutput realTime = new TimeOutput();

    private TimeSystem() {
    }

    public static TimeSystem getInstance() {
        if (instance == null) {
            instance = new TimeSystem();
        }

        return instance;
    }

    public void tick() {
        this.updateRealTime();
        if (!this.isPaused) {
            this.totalTicks++;
            this.totalSeconds = this.totalSeconds + 1.0F / this.tickRate * this.timeScale;
            this.smoothTime = this.smoothTime + 0.05F * this.timeScale;
            this.preventOverflow();
        }

        this.updateOutputs();
    }

    public void onRenderTick() {
        this.updateRealTime();
        this.updateOutputs();
    }

    private void updateRealTime() {
        if (this.useRealTime) {
            long currentTimeMillis = System.currentTimeMillis();
            this.realTimeSeconds = (float) (currentTimeMillis - this.startTimeMillis) / 1000.0F * this.timeScale;
            if (this.realTimeSeconds >= MAX_SHADER_TIME) {
                this.startTimeMillis = currentTimeMillis;
                this.realTimeSeconds = 0.0F;
            }
        }
    }

    private void preventOverflow() {
        if (this.totalTicks >= MAX_TICK_CYCLE) {
            this.totalTicks %= MAX_TICK_CYCLE;
        }

        if (this.totalSeconds >= MAX_SHADER_TIME) {
            this.totalSeconds %= MAX_SHADER_TIME;
        }

        if (this.smoothTime >= MAX_FLOAT_CYCLE) {
            this.smoothTime %= MAX_FLOAT_CYCLE;
        }
    }

    private void updateOutputs() {
        this.gameTime.update(this.totalTicks, this.totalSeconds, this.totalSeconds);
        float shaderSeconds = this.totalSeconds % MAX_FLOAT_CYCLE;
        this.shaderTime.update(this.totalTicks, shaderSeconds, shaderSeconds * 0.1F);
        float animSeconds = this.smoothTime % MAX_FLOAT_CYCLE;
        this.animationTime.update(this.totalTicks, animSeconds, animSeconds * 0.01F);
        float realSeconds = this.realTimeSeconds % MAX_FLOAT_CYCLE;
        this.realTime.update(this.totalTicks, realSeconds, realSeconds * 0.1F);
    }

    public TimeOutput getGameTime() {
        return this.gameTime;
    }

    public TimeOutput getShaderTime() {
        return this.shaderTime;
    }

    public TimeOutput getAnimationTime() {
        return this.animationTime;
    }

    public TimeOutput getRealTime() {
        return this.realTime;
    }

    public float getShaderTimeValue() {
        TimeOutput timeSource = this.useRealTime ? this.realTime : this.shaderTime;
        return timeSource.getSeconds() / 20.0F;
    }

    public float getFastShaderTime() {
        TimeOutput timeSource = this.useRealTime ? this.realTime : this.shaderTime;
        return timeSource.getSeconds() * 2.0F;
    }

    public float getSlowShaderTime() {
        TimeOutput timeSource = this.useRealTime ? this.realTime : this.shaderTime;
        return timeSource.getSeconds() * 0.5F;
    }

    public float getBreathingTime(float intensity) {
        TimeOutput timeSource = this.useRealTime ? this.realTime : this.shaderTime;
        return timeSource.getPulseTime(4.0F, 1.0F - intensity, 1.0F + intensity);
    }

    public void setTimeScale(float scale) {
        this.timeScale = Math.max(0.0F, scale);
    }

    public float getTimeScale() {
        return this.timeScale;
    }

    public void setPaused(boolean paused) {
        this.isPaused = paused;
    }

    public boolean isPaused() {
        return this.isPaused;
    }

    public void setUseRealTime(boolean useRealTime) {
        this.useRealTime = useRealTime;
    }

    public boolean isUsingRealTime() {
        return this.useRealTime;
    }

    public void reset() {
        this.totalTicks = 0L;
        this.totalSeconds = 0.0F;
        this.smoothTime = 0.0F;
        this.startTimeMillis = System.currentTimeMillis();
        this.realTimeSeconds = 0.0F;
        this.updateOutputs();
    }

    public void setTickRate(float rate) {
        this.tickRate = Math.max(1.0F, rate);
    }

    public static class TimeOutput {
        private long ticks;
        private float seconds;
        private float normalizedTime;
        private float smoothDelta;

        private void update(long ticks, float seconds, float smoothDelta) {
            this.ticks = ticks;
            this.seconds = seconds;
            this.normalizedTime = seconds % 60.0F / 60.0F;
            this.smoothDelta = smoothDelta;
        }

        public long getTicks() {
            return this.ticks;
        }

        public float getSeconds() {
            return this.seconds;
        }

        public float getMilliseconds() {
            return this.seconds * 1000.0F;
        }

        public float getNormalizedTime() {
            return this.normalizedTime;
        }

        public float getInterpolatedSeconds(float partialTicks) {
            return this.seconds + partialTicks / 20.0F;
        }

        public float getInterpolatedCyclicTime(float period, float partialTicks) {
            float interpolatedSeconds = this.getInterpolatedSeconds(partialTicks);
            return interpolatedSeconds % period / period;
        }

        public float getInterpolatedSineTime(float period, float partialTicks) {
            float cyclicTime = this.getInterpolatedCyclicTime(period, partialTicks);
            return (float) Math.sin(cyclicTime * Math.PI * 2.0);
        }

        public float getInterpolatedCosineTime(float period, float partialTicks) {
            float cyclicTime = this.getInterpolatedCyclicTime(period, partialTicks);
            return (float) Math.cos(cyclicTime * Math.PI * 2.0);
        }

        public float getInterpolatedPulseTime(float period, float minValue, float maxValue, float partialTicks) {
            float cyclicTime = this.getInterpolatedCyclicTime(period, partialTicks);
            float sine = (float) Math.sin(cyclicTime * Math.PI * 2.0) * 0.5F + 0.5F;
            return minValue + sine * (maxValue - minValue);
        }

        public float getCyclicTime(float period) {
            return this.seconds % period / period;
        }

        public float getSineTime(float period) {
            return (float) Math.sin((this.seconds % period / period) * Math.PI * 2.0);
        }

        public float getCosineTime(float period) {
            return (float) Math.cos((this.seconds % period / period) * Math.PI * 2.0);
        }

        public float getSmoohDelta() {
            return this.smoothDelta;
        }

        public float getPulseTime(float period, float minValue, float maxValue) {
            float normalized = this.getCyclicTime(period);
            float sine = (float) Math.sin(normalized * Math.PI * 2.0) * 0.5F + 0.5F;
            return minValue + sine * (maxValue - minValue);
        }
    }
}
