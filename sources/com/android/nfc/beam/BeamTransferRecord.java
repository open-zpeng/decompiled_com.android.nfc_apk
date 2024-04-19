package com.android.nfc.beam;

import android.bluetooth.BluetoothDevice;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
/* loaded from: classes.dex */
public class BeamTransferRecord implements Parcelable {
    public int dataLinkType;
    public int id;
    public boolean remoteActivating;
    public BluetoothDevice remoteDevice;
    public Uri[] uris;
    public static int DATA_LINK_TYPE_BLUETOOTH = 0;
    public static final Parcelable.Creator<BeamTransferRecord> CREATOR = new Parcelable.Creator<BeamTransferRecord>() { // from class: com.android.nfc.beam.BeamTransferRecord.1
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public BeamTransferRecord createFromParcel(Parcel in) {
            int deviceType = in.readInt();
            if (deviceType != BeamTransferRecord.DATA_LINK_TYPE_BLUETOOTH) {
                return null;
            }
            BluetoothDevice remoteDevice = (BluetoothDevice) in.readParcelable(getClass().getClassLoader());
            boolean remoteActivating = in.readInt() == 1;
            int numUris = in.readInt();
            Uri[] uris = null;
            if (numUris > 0) {
                uris = new Uri[numUris];
                in.readTypedArray(uris, Uri.CREATOR);
            }
            return new BeamTransferRecord(remoteDevice, remoteActivating, uris);
        }

        /* JADX WARN: Can't rename method to resolve collision */
        @Override // android.os.Parcelable.Creator
        public BeamTransferRecord[] newArray(int size) {
            return new BeamTransferRecord[size];
        }
    };

    private BeamTransferRecord(BluetoothDevice remoteDevice, boolean remoteActivating, Uri[] uris) {
        this.id = 0;
        this.remoteDevice = remoteDevice;
        this.remoteActivating = remoteActivating;
        this.uris = uris;
        this.dataLinkType = DATA_LINK_TYPE_BLUETOOTH;
    }

    public static final BeamTransferRecord forBluetoothDevice(BluetoothDevice remoteDevice, boolean remoteActivating, Uri[] uris) {
        return new BeamTransferRecord(remoteDevice, remoteActivating, uris);
    }

    @Override // android.os.Parcelable
    public int describeContents() {
        return 0;
    }

    @Override // android.os.Parcelable
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.dataLinkType);
        dest.writeParcelable(this.remoteDevice, 0);
        dest.writeInt(this.remoteActivating ? 1 : 0);
        Uri[] uriArr = this.uris;
        dest.writeInt(uriArr != null ? uriArr.length : 0);
        Uri[] uriArr2 = this.uris;
        if (uriArr2 != null && uriArr2.length > 0) {
            dest.writeTypedArray(uriArr2, 0);
        }
    }
}
