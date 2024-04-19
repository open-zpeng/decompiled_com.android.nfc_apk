package com.android.nfc;
/* loaded from: classes.dex */
public final class NfcDiscoveryParameters {
    static final int NFC_POLL_DEFAULT = -1;
    private int mTechMask = 0;
    private boolean mEnableLowPowerDiscovery = true;
    private boolean mEnableReaderMode = false;
    private boolean mEnableHostRouting = false;
    private boolean mEnableP2p = false;

    /* loaded from: classes.dex */
    public static class Builder {
        private NfcDiscoveryParameters mParameters;

        private Builder() {
            this.mParameters = new NfcDiscoveryParameters();
        }

        public Builder setTechMask(int techMask) {
            this.mParameters.mTechMask = techMask;
            return this;
        }

        public Builder setEnableLowPowerDiscovery(boolean enable) {
            this.mParameters.mEnableLowPowerDiscovery = enable;
            return this;
        }

        public Builder setEnableReaderMode(boolean enable) {
            this.mParameters.mEnableReaderMode = enable;
            if (enable) {
                this.mParameters.mEnableLowPowerDiscovery = false;
            }
            return this;
        }

        public Builder setEnableHostRouting(boolean enable) {
            this.mParameters.mEnableHostRouting = enable;
            return this;
        }

        public Builder setEnableP2p(boolean enable) {
            this.mParameters.mEnableP2p = enable;
            return this;
        }

        public NfcDiscoveryParameters build() {
            if (this.mParameters.mEnableReaderMode && (this.mParameters.mEnableLowPowerDiscovery || this.mParameters.mEnableP2p)) {
                throw new IllegalStateException("Can't enable LPTD/P2P and reader mode simultaneously");
            }
            return this.mParameters;
        }
    }

    public int getTechMask() {
        return this.mTechMask;
    }

    public boolean shouldEnableLowPowerDiscovery() {
        return this.mEnableLowPowerDiscovery;
    }

    public boolean shouldEnableReaderMode() {
        return this.mEnableReaderMode;
    }

    public boolean shouldEnableHostRouting() {
        return this.mEnableHostRouting;
    }

    public boolean shouldEnableDiscovery() {
        return this.mTechMask != 0 || this.mEnableHostRouting;
    }

    public boolean shouldEnableP2p() {
        return this.mEnableP2p;
    }

    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        NfcDiscoveryParameters params = (NfcDiscoveryParameters) obj;
        if (this.mTechMask == params.mTechMask && this.mEnableLowPowerDiscovery == params.mEnableLowPowerDiscovery && this.mEnableReaderMode == params.mEnableReaderMode && this.mEnableHostRouting == params.mEnableHostRouting && this.mEnableP2p == params.mEnableP2p) {
            return true;
        }
        return false;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (this.mTechMask == -1) {
            sb.append("mTechMask: default\n");
        } else {
            sb.append("mTechMask: " + Integer.toString(this.mTechMask) + "\n");
        }
        sb.append("mEnableLPD: " + Boolean.toString(this.mEnableLowPowerDiscovery) + "\n");
        sb.append("mEnableReader: " + Boolean.toString(this.mEnableReaderMode) + "\n");
        sb.append("mEnableHostRouting: " + Boolean.toString(this.mEnableHostRouting) + "\n");
        StringBuilder sb2 = new StringBuilder();
        sb2.append("mEnableP2p: ");
        sb2.append(Boolean.toString(this.mEnableP2p));
        sb.append(sb2.toString());
        return sb.toString();
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static NfcDiscoveryParameters getDefaultInstance() {
        return new NfcDiscoveryParameters();
    }

    public static NfcDiscoveryParameters getNfcOffParameters() {
        return new NfcDiscoveryParameters();
    }
}
