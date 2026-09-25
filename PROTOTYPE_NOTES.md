# Prototype design notes

This prototype intentionally keeps the first binary small and local-only.

The BLE protocol layer follows the public S400/MiBeacon frame description used by current open-source S400 tooling: service UUID 0xFE95, AES-CCM with a 16-byte bindkey, S400 object ID 0x6E16, and the 9-byte S400 object layout.

The next iteration should add:
- measurement sessions with explicit start/end IDs
- monotonic timestamps for synchronization with posture/video events
- a raw-frame/debug export mode
- derived body-composition estimates kept separate from raw measurements
- CSV schema versioning
- optional live-GATT mode if the extra Mi Home v2 authentication material is available
