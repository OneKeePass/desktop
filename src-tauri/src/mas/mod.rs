// To make all code for the feature "mas-build" active in VS code,
// Use #[cfg(any(feature = "mas-build", rust_analyzer))]
// Also need to ensure that rust_analyzer cfg is used in  src-tauri/build.rs

cfg_if::cfg_if! {
    if #[cfg(any(feature = "mas-build", rust_analyzer))] {
        pub(crate) mod bookmarks;
        mod real;
        pub(crate) use real::*;
    } else {
        mod noop;
        pub(crate) use noop::*;
    }
}

// Previously used

// Active for the cases when we do not use "mas-build" feature so that the code is grayed out for that
/*
#[cfg(any(feature = "mas-build"))]
pub(crate) mod bookmarks;

#[cfg(any(feature = "mas-build"))]
mod real;

#[cfg(not(any(feature = "mas-build")))]
mod noop;

#[cfg(not(any(feature = "mas-build")))]
pub(crate) use noop::*;

#[cfg(any(feature = "mas-build"))]
pub(crate) use real::*;
*/

// To make all code for the feature "mas-build" active in VS code, we need to do the following
// Uncomment the following. Comment the above
// Also need to ensure that rust_analyzer cfg is used in  src-tauri/build.rs

/*
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
*/
