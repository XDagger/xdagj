# Firefly(XDAGj-v0.8.4)



## Firefly v0.8.4

- Fix abnormally high generation of orphan blocks issues.



### Notable changes in this release:

-Implement thread safety for increasing or decreasing xxnoref to ensure normal block production.

### Bug fixes:

-Fixed an issue with inconsistent synchronization of orphan block statistics in a multi-threaded environment.
