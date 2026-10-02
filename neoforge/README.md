# NeoForge 1.21.1 adapter

Implemented node: `1.21.1-neoforge`, Java 21, ModDevGradle 2.0.140 / Mojmap.

This directory contains NeoForge registration, lifecycle, payload, wood hooks,
ModelData and baked-model adapters. Game rules, geometry, materials, payment,
placement, door rendering and harness assertions remain in `common/`.
Copycats+ is optional and is loaded only with `-PwithCopycats` in development.

See [MULTILOADER.md](../MULTILOADER.md) for commands and pinned dependencies.
Minecraft 1.20.1 NeoForge uses the existing Forge jar and has no separate node.
