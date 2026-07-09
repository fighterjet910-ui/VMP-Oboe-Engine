#include <jni.h>
#include <cmath>
#include <atomic>
#include <vector>
#include <algorithm>
#include <arm_neon.h>
#include <Oboe.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

// ==========================================
// Virtualization (VMP) Macros Definition (CRASH FIXED)
// ==========================================
// Processor ab in bytes ko execute nahi karega, balki b 1f ki madad se inke oopar se jump kar jayega.
#define VMP_BEGIN_MUTATION() __asm__ volatile("b 1f\n\t.byte 0xEB, 0x10, 0x56, 0x4D, 0x50, 0x01\n\t.balign 4\n1:");
#define VMP_END_MUTATION()   __asm__ volatile("b 1f\n\t.byte 0xEB, 0x0E, 0x56, 0x4D, 0x50, 0x02\n\t.balign 4\n1:");

struct SIMDBiquadState {
    float32x4_t x1 = vdupq_n_f32(0.0f);
    float32x4_t x2 = vdupq_n_f32(0.0f);
    float32x4_t y1 = vdupq_n_f32(0.0f);
    float32x4_t y2 = vdupq_n_f32(0.0f);
};

struct UltimateFilterBand {
    float target_b0, target_b1, target_b2, target_a1, target_a2;
    float b0 = 0.0f, b1 = 0.0f, b2 = 0.0f, a1 = 0.0f, a2 = 0.0f;
    SIMDBiquadState stateL; 
    SIMDBiquadState stateR; 

    void reset() { stateL = SIMDBiquadState(); stateR = SIMDBiquadState(); }

    void calculatePeakingEQ(float frequency, float sampleRate, float gainDb, float Q) {
        float A = std::pow(10.0f, gainDb / 40.0f);
        float omega = 2.0f * (float)M_PI * frequency / sampleRate;
        float alpha = std::sin(omega) / (2.0f * Q);
        float cosOmega = std::cos(omega);
        float norm = 1.0f + alpha / A;

        target_b0 = (1.0f + alpha * A) / norm;
        target_b1 = (-2.0f * cosOmega) / norm;
        target_b2 = (1.0f - alpha * A) / norm;
        target_a1 = (-2.0f * cosOmega) / norm;
        target_a2 = (1.0f - alpha / A) / norm;
        
        if (b0 == 0.0f) { b0 = target_b0; b1 = target_b1; b2 = target_b2; a1 = target_a1; a2 = target_a2; }
    }

    void rampCoefficients() {
        const float ramp = 0.005f; 
        b0 += (target_b0 - b0) * ramp; b1 += (target_b1 - b1) * ramp; b2 += (target_b2 - b2) * ramp;
        a1 += (target_a1 - a1) * ramp; a2 += (target_a2 - a2) * ramp;
    }
};

class EliteAudioProcessor {
private:
    std::vector<UltimateFilterBand> filterBands;
    std::vector<float> deInterleavedL;
    std::vector<float> deInterleavedR;
    std::vector<float> lookAheadBufferL;
    std::vector<float> lookAheadBufferR;
    
    size_t lookAheadIndex = 0;
    size_t lookAheadDelaySamples = 48; 

    float currentGain = 1.0f;
    const float safety_ceiling = 0.35f;   
    const float footstep_floor = 0.03f;   
    const float attack_coeff = 0.98f;     
    const float release_coeff = 0.002f;   

public:
    EliteAudioProcessor() {
        VMP_BEGIN_MUTATION(); 
        filterBands.resize(10);
        float frequencies[10] = {60.f, 150.f, 400.f, 1000.f, 2500.f, 4000.f, 6000.f, 8000.f, 10000.f, 12000.f};
        float defaultGains[10] = {-15.0f, -9.0f, 6.0f, 9.0f, 8.0f, 4.0f, -3.0f, -4.0f, -5.0f, -5.0f};
        
        for (int i = 0; i < 10; ++i) {
            filterBands[i].reset();
            filterBands[i].calculatePeakingEQ(frequencies[i], 48000.0f, defaultGains[i], 1.3f);
        }

        lookAheadBufferL.assign(lookAheadDelaySamples, 0.0f);
        lookAheadBufferR.assign(lookAheadDelaySamples, 0.0f);
        VMP_END_MUTATION();
    }

    void setBandGain(int index, float gainDb) {
        if (index >= 0 && index < 10) {
            float frequencies[10] = {60.f, 150.f, 400.f, 1000.f, 2500.f, 4000.f, 6000.f, 8000.f, 10000.f, 12000.f};
            filterBands[index].calculatePeakingEQ(frequencies[index], 48000.0f, gainDb, 1.3f);
        }
    }

    void processAudioSIMD(float* buffer, int totalSamples) {
        int halfSize = totalSamples / 2;
        
        if (deInterleavedL.size() < (size_t)halfSize) {
            deInterleavedL.resize(halfSize);
            deInterleavedR.resize(halfSize);
        }

        // Step 1: De-interleave
        for (int i = 0, j = 0; i < totalSamples; i += 2, ++j) {
            deInterleavedL[j] = buffer[i];
            deInterleavedR[j] = buffer[i + 1];
        }

        // Step 2: EQ Filtering Loop (NEON SIMD Vectorized)
        for (auto& filter : filterBands) {
            filter.rampCoefficients();
            float32x4_t vb0 = vdupq_n_f32(filter.b0), vb1 = vdupq_n_f32(filter.b1), vb2 = vdupq_n_f32(filter.b2);
            float32x4_t va1 = vdupq_n_f32(filter.a1), va2 = vdupq_n_f32(filter.a2);

            int s = 0;
            for (; s <= halfSize - 4; s += 4) {
                float32x4_t inL = vld1q_f32(&deInterleavedL[s]);
                float32x4_t outL = vsubq_f32(vsubq_f32(vmlaq_f32(vmlaq_f32(vmulq_f32(inL, vb0), filter.stateL.x1, vb1), filter.stateL.x2, vb2), vmulq_f32(filter.stateL.y1, va1)), vmulq_f32(filter.stateL.y2, va2));
                filter.stateL.x2 = filter.stateL.x1; filter.stateL.x1 = inL;
                filter.stateL.y2 = filter.stateL.y1; filter.stateL.y1 = outL;
                vst1q_f32(&deInterleavedL[s], outL);

                float32x4_t inR = vld1q_f32(&deInterleavedR[s]);
                float32x4_t outR = vsubq_f32(vsubq_f32(vmlaq_f32(vmlaq_f32(vmulq_f32(inR, vb0), filter.stateR.x1, vb1), filter.stateR.x2, vb2), vmulq_f32(filter.stateR.y1, va1)), vmulq_f32(filter.stateR.y2, va2));
                filter.stateR.x2 = filter.stateR.x1; filter.stateR.x1 = inR;
                filter.stateR.y2 = filter.stateR.y1; filter.stateR.y1 = outR;
                vst1q_f32(&deInterleavedR[s], outR);
            }

            for (; s < halfSize; ++s) {
                float l_val = deInterleavedL[s], r_val = deInterleavedR[s];
                float outL = filter.b0 * l_val + filter.b1 * vgetq_lane_f32(filter.stateL.x1, 0) + filter.b2 * vgetq_lane_f32(filter.stateL.x2, 0) - filter.a1 * vgetq_lane_f32(filter.stateL.y1, 0) - filter.a2 * vgetq_lane_f32(filter.stateL.y2, 0);
                filter.stateL.x2 = filter.stateL.x1; filter.stateL.x1 = vdupq_n_f32(l_val);
                filter.stateL.y2 = filter.stateL.y1; filter.stateL.y1 = vdupq_n_f32(outL);
                deInterleavedL[s] = outL;

                float outR = filter.b0 * r_val + filter.b1 * vgetq_lane_f32(filter.stateR.x1, 0) + filter.b2 * vgetq_lane_f32(filter.stateR.x2, 0) - filter.a1 * vgetq_lane_f32(filter.stateR.y1, 0) - filter.a2 * vgetq_lane_f32(filter.stateR.y2, 0);
                filter.stateR.x2 = filter.stateR.x1; filter.stateR.x1 = vdupq_n_f32(r_val);
                filter.stateR.y2 = filter.stateR.y1; filter.stateR.y1 = vdupq_n_f32(outR);
                deInterleavedR[s] = outR;
            }
        }

        // 🔥 Step 3: AUTO 360° SPATIAL AWARENESS (SIMD Mid-Side Matrix)
        float32x4_t v_half = vdupq_n_f32(0.5f);
        float32x4_t v_spatial_width = vdupq_n_f32(1.55f); 
        
        int s = 0;
        for (; s <= halfSize - 4; s += 4) {
            float32x4_t l = vld1q_f32(&deInterleavedL[s]);
            float32x4_t r = vld1q_f32(&deInterleavedR[s]);
            
            float32x4_t mid = vmulq_f32(vaddq_f32(l, r), v_half);
            float32x4_t side = vmulq_f32(vsubq_f32(l, r), v_half);
            side = vmulq_f32(side, v_spatial_width);
            
            vst1q_f32(&deInterleavedL[s], vaddq_f32(mid, side));
            vst1q_f32(&deInterleavedR[s], vsubq_f32(mid, side));
        }
        
        for (; s < halfSize; ++s) {
            float l = deInterleavedL[s];
            float r = deInterleavedR[s];
            float mid = (l + r) * 0.5f;
            float side = (l - r) * 0.5f * 1.55f;
            deInterleavedL[s] = mid + side;
            deInterleavedR[s] = mid - side;
        }

        // Step 4: Look-Ahead Limiter Engine
        for (int i = 0; i < halfSize; ++i) {
            float futureL = deInterleavedL[i], futureR = deInterleavedR[i];
            float future_peak = std::max(std::abs(futureL), std::abs(futureR));
            float targetGain = 1.0f;

            if (future_peak > safety_ceiling) {
                targetGain = safety_ceiling / (future_peak + 1e-5f);
                currentGain = currentGain * (1.0f - attack_coeff) + targetGain * attack_coeff;
            } else if (future_peak < footstep_floor && future_peak > 0.004f) {
                targetGain = 1.6f;
                currentGain = currentGain * (1.0f - release_coeff) + targetGain * release_coeff;
            } else {
                currentGain = currentGain * (1.0f - release_coeff) + 1.0f * release_coeff;
            }

            float currentL = lookAheadBufferL[lookAheadIndex], currentR = lookAheadBufferR[lookAheadIndex];
            lookAheadBufferL[lookAheadIndex] = futureL; lookAheadBufferR[lookAheadIndex] = futureR;
            lookAheadIndex = (lookAheadIndex + 1) % lookAheadDelaySamples;

            buffer[i * 2] = currentL * currentGain;
            buffer[i * 2 + 1] = currentR * currentGain;
        }
    }
};

class OboeAudioStreamEngine : public oboe::AudioStreamDataCallback {
private:
    std::shared_ptr<oboe::AudioStream> mStream;
    EliteAudioProcessor* mProcessor;

public:
    OboeAudioStreamEngine(EliteAudioProcessor* processor) : mProcessor(processor) {}

    bool start() {
        VMP_BEGIN_MUTATION(); 
        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Output)
               ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
               ->setSharingMode(oboe::SharingMode::Shared)
               ->setFormat(oboe::AudioFormat::Float)
               ->setChannelCount(oboe::ChannelCount::Stereo)
               ->setSampleRate(48000)
               ->setDataCallback(this);
               
        oboe::Result result = builder.openStream(mStream);
        if (result != oboe::Result::OK) return false;
        
        result = mStream->requestStart();
        VMP_END_MUTATION();
        
        return result == oboe::Result::OK;
    }

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *audioStream, void *audioData, int32_t numFrames) override {
        auto *floatData = static_cast<float *>(audioData);
        int totalSamples = numFrames * audioStream->getChannelCount();
        if (mProcessor != nullptr && floatData != nullptr) mProcessor->processAudioSIMD(floatData, totalSamples);
        return oboe::DataCallbackResult::Continue;
    }

    void stop() { if (mStream) { mStream->requestStop(); mStream->close(); } }
};

extern "C" {
    struct EngineContainer { EliteAudioProcessor* processor; OboeAudioStreamEngine* streamEngine; };

    JNIEXPORT jlong JNICALL Java_com_audio_sound_master_PremiumAudioService_initNativeEngine(JNIEnv* env, jobject thiz) {
        VMP_BEGIN_MUTATION();
        auto* container = new EngineContainer();
        container->processor = new EliteAudioProcessor();
        container->streamEngine = new OboeAudioStreamEngine(container->processor);
        
        if (!container->streamEngine->start()) {
            delete container->streamEngine; delete container->processor; delete container; return 0;
        }
        VMP_END_MUTATION();
        return reinterpret_cast<jlong>(container);
    }

    JNIEXPORT void JNICALL Java_com_audio_sound_master_PremiumAudioService_setNativeBandGain(JNIEnv* env, jobject thiz, jlong handle, jint band, jfloat gain) {
        auto* container = reinterpret_cast<EngineContainer*>(handle);
        if (container && container->processor) container->processor->setBandGain(band, gain);
    }

    JNIEXPORT void JNICALL Java_com_audio_sound_master_PremiumAudioService_releaseNativeEngine(JNIEnv* env, jobject thiz, jlong handle) {
        auto* container = reinterpret_cast<EngineContainer*>(handle);
        if (container) {
            if (container->streamEngine) { container->streamEngine->stop(); delete container->streamEngine; }
            if (container->processor) delete container->processor;
            delete container;
        }
    }
}
