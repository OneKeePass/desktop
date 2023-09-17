use serde::{Serialize, Deserialize};
use swift_rs::{swift, Int, Int32, SRObjectArray, SRString};


#[repr(C)]
struct WindowDetail {
  name: Option<SRString>,
  owner: Option<SRString>,
  pid:Int,
}

swift!(fn auto_type_window_titles() -> SRObjectArray<WindowDetail>);
swift!(fn auto_type_activate_window(pid:Int32));

fn window_titles() {
    let widows:SRObjectArray<WindowDetail> = unsafe { auto_type_window_titles() };

    for w in widows.as_slice() {
        println!(" name is {} with process id {}", 
        &w.name.as_ref().map_or("", |s| s.as_str()), &w.pid);
    }

}

fn activate_window(pid:i32) {
  unsafe {auto_type_activate_window(pid);}
  println!("Activated window pid {}", pid);
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TestArg {
  cmd_name:String,
  pid:Option<i32>,
}

pub fn test_call(arg:TestArg) {
    match arg.cmd_name.as_str() {
      "window_titles"  => window_titles(),
      "activate_window" => {
        if let Some(p) = arg.pid {
          activate_window(p);
        } else {
          println!("activate_window is not called");
        }
      }
      _ => { println!("Unknown commnad in arg {:?}", arg)}
    }
    
}

