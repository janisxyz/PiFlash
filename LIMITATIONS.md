# Known limitations

## USB mass storage

- Direct block-device paths are not available to unprivileged Android apps.
- PiFlash uses USB MSC Bulk-Only Transport + SCSI WRITE/READ/SYNCHRONIZE CACHE.
- Multi-LUN hubs, some cheap readers, and phones with broken USB host stacks may fail.
- Capacity is read via SCSI READ CAPACITY (10); cards >2 TiB need READ CAPACITY (16) (not yet implemented).

## FAT configuration writer

- Only FAT32 boot partitions are supported for post-flash config file injection.
- 8.3 short names are used for config files.
- Very full root directories can fail allocation.

## Password hashing

- Android does not ship `crypt(3)` yescrypt. PiFlash emits a `$6$` SHA-512-style hash.

## Verification

- Full bit-for-bit verify of multi-GB images is slow over USB; bookend + flush checks are used by default.
