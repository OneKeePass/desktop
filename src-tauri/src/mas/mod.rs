#[cfg(any(feature = "mas-build", rust_analyzer))]
pub(crate) mod bookmarks;

#[cfg(any(feature = "mas-build", rust_analyzer))]
mod real;

#[cfg(not(any(feature = "mas-build", rust_analyzer)))]
mod noop;

#[cfg(not(any(feature = "mas-build", rust_analyzer)))]
pub(crate) use noop::*;

#[cfg(any(feature = "mas-build", rust_analyzer))]
pub(crate) use real::*;
