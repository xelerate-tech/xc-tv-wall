This folder is git-ignored (except this file). Put here:

  service-account.json   Google Cloud service-account key (JSON)
  config.properties      driveFolderId=<id from the Drive folder URL>
  release.keystore       signing key — BACK IT UP; every update must use the same key
  keystore.properties    storeFile=release.keystore
                         storePassword=...
                         keyAlias=xc-tv-wall
                         keyPassword=...

See ../README.md for how to create each one.
