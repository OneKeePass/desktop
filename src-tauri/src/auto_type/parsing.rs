use std::{
  collections::{HashMap, HashSet},
  sync::OnceLock,
};

use nom::{
  branch::alt,
  bytes::complete::{tag, tag_no_case, take_until},
  character::complete::{alpha0, char, multispace0},
  character::complete::{alphanumeric1, digit0, multispace1},
  combinator::{map, map_parser, map_res, opt, rest, verify},
  error::Error,
  multi::many1,
  sequence::tuple,
  IResult,
};
use serde::{Deserialize, Serialize};

// From nom::internal  type IResult<I, O, E = (I, ErrorKind)> = Result<(I, O), Err<E>>;

static STANDARD_FIELDS: OnceLock<HashSet<&'static str>> = OnceLock::new();

fn standard_fields() -> &'static HashSet<&'static str> {
  STANDARD_FIELDS.get_or_init(|| HashSet::from(["TITLE", "USERNAME", "PASSWORD", "URL"]))
}

static KEY_NAMES: OnceLock<HashSet<&'static str>> = OnceLock::new();

fn key_names() -> &'static HashSet<&'static str> {
  KEY_NAMES.get_or_init(|| HashSet::from(["TAB", "ENTER", "SPACE"]))
}

#[derive(Debug, PartialEq, Eq)]
enum PlaceHolder<'a> {
  Attribute(&'a str),
  KeyName(&'a str, i32), //include optional repeat field
  Delay(i32),
  KeyPressDelay(i32),
  Modfier(Vec<char>),
}

// Extracts any string value between { ... }
fn fenced_name<'a>() -> impl FnMut(&'a str) -> IResult<&'a str, &'a str> {
  map(
    // tuple returns output for each parser listed in an output tuple
    tuple((multispace0, tag("{"), take_until("}"), tag("}"))),
    //Here x is a tuple
    |x| x.2,
  )
}

// Extracts the modifiers if any with the an optional single letter at the end.
// The parser 'verify' ensures that we have only one charater at the end
fn modifier_parser<'a>() -> impl FnMut(&'a str) -> IResult<&'a str, PlaceHolder<'a>> {
  map(
    tuple((
      multispace0,
      many1(alt((char('+'), char('^'), char('%'), char('#')))),
      verify(opt(alpha0), |x| x.map_or(true, |v: &str| v.len() == 1)),
      multispace0,
    )),
    |(_, chrs, _letter, _)| PlaceHolder::Modfier(chrs),
  )
}

// Extracts the attribute name and verifies that the name is accepted one
fn standard_field_parser<'a>() -> impl FnMut(&'a str) -> IResult<&'a str, PlaceHolder<'a>> {
  map(
    verify(
      map(
        tuple((
          multispace0,
          tag("{"),
          take_until("}"),
          tag("}"),
          multispace0,
        )),
        // x is a tuple and map call returns the actual extracted value from third member ( index 2 - take_until output) of tuple
        |x| x.2,
      ),
      // Verifies that the map call returned value
      move |v: &str| standard_fields().contains(&v.to_string().to_uppercase().as_str()),
    ),
    // The input 'x' is the returned value from the earlier 'map' parser on successful verification
    |x| PlaceHolder::Attribute(x),
  )
}

// Extracts the attribute name and verifies that the name is accepted one
fn custom_field_parser<'a>(
  entry_fields: &'a HashMap<String, String>,
) -> impl FnMut(&'a str) -> IResult<&'a str, PlaceHolder<'a>> {
  map(
    verify(
      map(
        tuple((
          multispace0,
          tag("{"),
          multispace0,
          tag("S:"),
          take_until("}"),
          tag("}"),
          multispace0,
        )),
        // x is a tuple and map call returns the actual extracted value from fifth member ( index 5) of tuple
        |x| x.4,
      ),
      // Verifies that the map call returned key value that is found in 'entry_fields'
      move |v: &str| entry_fields.contains_key(v.to_uppercase().as_str()),
    ),
    // The input 'x' is the returned value from the earlier 'map' parser on successful verification
    |x| PlaceHolder::Attribute(x),
  )
}

fn to_num<'a>(default_val: i32) -> impl FnMut(&'a str) -> IResult<&'a str, i32> {
  map_res(digit0, move |d: &str| {
    if d.trim().is_empty() {
      Ok(default_val)
    } else {
      d.parse::<i32>()
    }
  })
}

// Gets any key name with any optional repeat value
// Repeat a key  x times (e.g., {SPACE 5} inserts five spaces)
fn key_name_opt_repeat_parser<'a>() -> impl FnMut(&'a str) -> IResult<&'a str, PlaceHolder<'a>> {
  // map_parser takes the result of the first parser 'fenced_name()' and tries to apply the second parser to it.
  // If both parsers succeed, then it returns the value.
  map_parser(
    fenced_name(),
    map(
      // verify parser ensures that the extracted value is indeed a supported key name
      verify(
        map(
          tuple((
            // https://stackoverflow.com/questions/70441646/cannot-infer-type-for-type-parameter-error-when-parsing-with-nom
            // Need to use type annotation as shown; Otherwise we will get the error
            // type annotations needed cannot infer type of the type
            // parameter `T` declared on the function
            multispace0::<&'a str, Error<_>>,
            alphanumeric1,
            multispace0,
            // opt(digit1),
            // recognize(opt(digit1)),
            tuple((to_num(1), rest)),
            //multispace0,
          )),
          |x| (x.1, x.3),
        ),
        |(v, r)| {
          r.1.trim().is_empty() && key_names().contains(&v.to_string().to_uppercase().as_str())
        },
      ),
      |(v, r)| PlaceHolder::KeyName(v, r.0),
    ),
  )
}

fn delay_parser<'a>() -> impl FnMut(&'a str) -> IResult<&'a str, PlaceHolder<'a>> {
  map_parser(
    fenced_name(),
    map(
      // The 'verify' parser verifies the the inner parser's output
      verify(
        map(
          tuple((
            multispace0,
            tag_no_case("delay"),
            multispace1,
            tuple((to_num(1), rest)),
          )),
          // Fn called by map parser where x is a tuple
          |x: (&str, &str, &str, (i32, &str))| x.3,
        ),
        // Fn called by verify parser. The arg is the output of inner map parser
        |(_v, r)| r.trim().is_empty(),
      ),
      // Fn called by outer map parser. The arg is the output of inner map parser
      |(v, _)| PlaceHolder::Delay(v),
    ),
  )
}

fn key_delay_parser<'a>() -> impl FnMut(&'a str) -> IResult<&'a str, PlaceHolder<'a>> {
  map_parser(
    fenced_name(),
    map(
      // The 'verify' parser verifies the the inner parser's output
      verify(
        map(
          tuple((
            multispace0,
            tag_no_case("delay"),
            multispace0,
            tag("="),
            multispace0,
            tuple((to_num(1), rest)),
          )),
          // Fn called by map parser where arg is a tuple from the 'tuple' parser
          |(_, _, _, _, _, (v, r))| (v, r),
        ),
        // Fn called by verify parser. The arg is the output of inner map parser
        |(_v, r)| r.trim().is_empty(),
      ),
      // Fn called by outer map parser. The arg is the output of inner map parser
      |(v, _)| PlaceHolder::KeyPressDelay(v),
    ),
  )
}

#[derive(Debug)]
struct PlaceHolderParser<'a> {
  input: &'a str,
  entry_fields: &'a HashMap<String, String>,
}

impl<'a> PlaceHolderParser<'a> {
  // IMPORTANT: All keys in entry_fields map are expected to be in upper case only
  // Caller needs to convert to uppercase
  fn new(input: &'a str, entry_fields: &'a HashMap<String, String>) -> Self {
    Self {
      input,
      entry_fields,
    }
  }

  fn parse(&mut self) -> Result<Vec<PlaceHolder>, String> {
    let mut out: Vec<PlaceHolder<'_>> = vec![];
    // Combine all parers that are used to parse a sequence string
    let mut parser = alt((
      modifier_parser(),
      delay_parser(),
      key_delay_parser(),
      standard_field_parser(),
      custom_field_parser(&self.entry_fields),
      key_name_opt_repeat_parser(),
    ));

    let mut current_input = self.input.trim();
    //IMPORTANT:
    // After parsing completely the sequence string, the end 'current_input' should be empty
    while !current_input.is_empty() {
      //println!("current_input is {}", &current_input);
      let r = parser(current_input);
      //println!("Parse r is {:?}", &r);
      match r {
        Ok((remaining, parsed)) => {
          out.push(parsed);
          current_input = remaining;
        }
        Err(e) => return Err(e.to_string()),
      }
    }
    Ok(out)
  }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ParsedPlaceHolderVal {
  Attribute(String),
  KeyName(String, i32), //include optional repeat field
  Delay(i32),
  KeyPressDelay(i32),
  Modfier(Vec<char>),
}

impl ParsedPlaceHolderVal {
  fn from_vals(vals: Vec<PlaceHolder<'_>>) -> Vec<ParsedPlaceHolderVal> {
    vals
      .into_iter()
      .map(|v| v.into())
      .collect::<Vec<ParsedPlaceHolderVal>>()
  }
}

impl<'a> From<PlaceHolder<'_>> for ParsedPlaceHolderVal {
  fn from(val: PlaceHolder<'_>) -> ParsedPlaceHolderVal {
    match val {
      // ensure that the attribute name is in upper case as this is used as key to
      // look up value from entru_fields hash map
      PlaceHolder::Attribute(s) => Self::Attribute(s.to_string().to_uppercase()),
      PlaceHolder::KeyName(s, v) => Self::KeyName(s.to_string(), v),
      PlaceHolder::Delay(v) => Self::Delay(v),
      PlaceHolder::KeyPressDelay(v) => Self::KeyPressDelay(v),
      PlaceHolder::Modfier(v) => Self::Modfier(v),
    }
  }
}

pub fn parse_auto_type_sequence(
  // This is the string that needs to be parsed
  sequence: &str,
  // This maps the field names to its values.
  // The parsed field names will be replaced with the actual value
  entry_fields: &HashMap<String, String>,
) -> Result<Vec<ParsedPlaceHolderVal>, String> {
  let entry_fields_case_converted: HashMap<String, String> = entry_fields
    .iter()
    .map(|(k, v)| (k.to_uppercase().clone(), v.clone()))
    .collect();

  let mut ph = PlaceHolderParser::new(sequence, &entry_fields_case_converted);
  let r = ph.parse();

  match r {
    Ok(vals) => Ok(ParsedPlaceHolderVal::from_vals(vals)),
    Err(e) => {
      // TODO: Print only keys !
      // debug!(
      //   "Parsing failed with error {} for the sequence {} , entry_fields {:?}, entry_fields_case_converted {:?} ",
      //   &e, sequence, &entry_fields, &entry_fields_case_converted
      // );
      Err(e)
    }
  }
}

// This fn is not working
// Error is :
// cannot return value referencing local variable `ph`returns a value referencing data owned by the current function
// So returning Vec<PlaceHolderVal> is used
// pub fn parse_auto_type_sequence_not_working<'a>(sequence:&'a str) -> Result<Vec<PlaceHolder<'_>>, String> {
//     let mut ph = PlaceHolderParser::<'a>::new(sequence);
//     let r = ph.parse();
//     r
// }

#[cfg(test)]
mod tests {
  use super::*;
  #[test]
  fn verify_parsing() {
    let sample1 =
      "^+d  {USERNAMe} {S:maiden name} {delay 5}  {tab 4   } %#M {delay=10 } {SPACE} {S:CUSTOMER NAME} {PASSWORD} {enter}   ";

    // All custom fields (other than standard fileds) are required to be present
    let entry_fields = HashMap::from([
      ("Maiden name".to_string(), "Some value".to_string()),
      ("CUSTOMER NAME".to_string(), "Some value".to_string()),
      // add other fields
    ]);

    let r = parse_auto_type_sequence(sample1, &entry_fields);

    println!("Parsed output is {:?}", r);
    assert!(r.is_ok())
  }

  #[test]
  fn verify_parsing_error() {
    // No place holder variable is found
    let sample1 = "Some random text";

    let entry_fields = HashMap::default();

    let r = parse_auto_type_sequence(sample1, &entry_fields);

    println!("Parsed output is {:?}", r);

    assert!(r.is_err())
  }
}
/*
// Gets the delay to use between the key presses to x milliseconds
fn key_delay_parser<'a>() -> impl FnMut(&'a str) -> IResult<&'a str, PlaceHolder> {
  map_parser(
    fenced_name(),
    map(
      tuple((
        multispace0,
        tag_no_case("delay"),
        multispace0,
        tag("="),
        multispace0,
        digit1,
        multispace0,
      )),
      |x| PlaceHolder::KeyPressDelay(x.5),
    ),
  )
}

fn key_name_opt_repeat_parser1<'a>() -> impl FnMut(&'a str) -> IResult<&'a str, PlaceHolder> {
  map_parser(
    fenced_name(),
    map_res(
      // verify parser ensures that the extracted value is indeed a supported key name
      verify(
        map(
          tuple((
            // Need to use type annotation as shown; Otherwise we will get the error
            // type annotations needed cannot infer type of the type
            // parameter `T` declared on the function
            multispace0::<&'a str, Error<_>>,
            alphanumeric1,
            multispace0,
            // opt(digit1),
            // recognize(opt(digit1)),
            tuple((digit0, rest)),
            //multispace0,
          )),
          |x| (x.1, x.3),
        ),
        |(v, r)| key_names().contains(&v.to_string().to_lowercase().as_str()),
      ),
      |(v, r)| -> Result<PlaceHolder, String> {
        println!(" r is {:?}", r);
        if !r.1.trim().is_empty() {
          return Err("Not empty....".into());
        } else {
          return Ok(PlaceHolder::KeyName(v, 0));
        }
      },
    ),
  )
}

fn par1(v: &str, r: (&str, &str)) {}


// Gets the key name only
fn _key_name_parser<'a>() -> impl FnMut(&'a str) -> IResult<&'a str, PlaceHolder> {
  map(
    verify(
      map(
        tuple((
          multispace0,
          tag("{"),
          take_until("}"),
          tag("}"),
          multispace0,
        )),
        |x| x.2,
      ),
      move |v: &str| key_names().contains(&v.to_string().to_lowercase().as_str()),
    ),
    |x| PlaceHolder::KeyName(x),
  )
}


const STANDARD_FIELDS: &[&'static str] = &["username", "password", "url"];
const KEY_NAMES: &[&'static str] = &["tab", "enter"];

#[derive(Debug, PartialEq, Eq)]
pub enum PlaceHolder1<'a> {
  Attribute(&'a str),
  Key(&'a str), //Change it to Action
}

impl<'a> PlaceHolder1<'a> {
  fn try_new(name: &'a str) -> Result<Self, String> {
    // TODO Use lazy static macro... to create a vec one time
    let atts = STANDARD_FIELDS.to_vec();
    let knames = KEY_NAMES.to_vec();
    let lname = name.to_lowercase();
    if atts.contains(&lname.as_str()) {
      return Ok(Self::Attribute(name));
    } else if knames.contains(&lname.as_str()) {
      return Ok(Self::Key(name));
    } else {
      return Err(format!(
        "{} is not a valid attribute or allowed key name",
        &name
      ));
    }
  }
}

struct PlaceHolderParser1<'a> {
  input: &'a str,
}

impl<'a> PlaceHolderParser1<'a> {
  fn new(input: &'a str) -> Self {
    Self { input }
  }

  fn parse(&mut self) -> Result<Vec<PlaceHolder1>, String> {
    let mut out = vec![];
    let mut current_input = self.input.trim();
    while !current_input.is_empty() {
      let r = fenced("{", "}")(current_input);
      match r {
        Ok((remaining, parsed)) => match PlaceHolder1::try_new(parsed) {
          Ok(ph) => {
            out.push(ph);
            current_input = remaining;
          }
          Err(e) => return Err(e),
        },
        Err(e) => return Err(e.to_string()),
      }
    }
    Ok(out)
  }
}

fn fenced_parser<'a>() {
  // map_res(

  // )
}

// Gets texts between two demiliters
fn fenced<'a>(start: &'a str, end: &'a str) -> impl FnMut(&'a str) -> IResult<&'a str, &'a str> {
  //   // x is a 3 member tuple
  //   map(tuple((tag(start),take_until(end), tag(end))), |x| x.1)

  // x is a 3 member tuple
  map(
    tuple((multispace0, tag(start), take_until(end), tag(end))),
    |x| x.2,
  )
}

// delay 10
// delay=10

fn name_with_delay_or_repeat<'a>() -> impl FnMut(&'a str) -> IResult<&'a str, (&'a str, &'a str)> {
  //map(tuple((tag_no_case("delay"),multispace0,digit1)),  |x| (x.0, x.2) )
  map(
    tuple((multispace0, alpha1, multispace0, digit1, multispace0)),
    |x| (x.1, x.3),
  )
}

fn name_with_delay<'a>() -> impl FnMut(&'a str) -> IResult<&'a str, &'a str> {
  map(
    tuple((
      multispace0,
      tag_no_case("delay"),
      multispace0,
      tag("="),
      multispace0,
      digit1,
      multispace0,
    )),
    |x| x.5,
  )
}

fn modifier_parser1<'a>() -> impl FnMut(&'a str) -> IResult<&'a str, (Vec<char>, Option<&str>)> {
  tuple((
    many0(alt((char('+'), char('^'), char('%'), char('#')))),
    opt(alpha0),
  ))
}


#[cfg(test)]
mod tests {
  use nom::{
    character::complete::{alpha0, alpha1, char},
    error::{self, ParseError},
  };

  use super::*;

  #[test]
  fn verify_parsing1() {
    let sample1 = "{USERNAMe}{PASSWORD}";
    let mut parser = attribute_parser();

    //let mut parser =  nom::multi::many1(parser);
    println!("Parsed output is {:?}", parser(sample1));
  }

  #[test]
  fn verify_parsing1_1() {
    let sample1 = "{USERNAME}{TAG}{PASSWORD}";
    let parser = fenced("{", "}");
    let parser = nom::combinator::map(parser, |x| x.len());
    let mut parser = nom::multi::many1(parser);
    println!("Parsed output is {:?}", parser(sample1));
  }

  #[test]
  fn verify_parsing2() {
    let sample1 = "^+d  {USERNAMe} {delay 5}  {tab} %#Mn {delay = 10} {PASSWORD}              ";
    let mut ph = PlaceHolderParser::new(sample1);
    let r = ph.parse();
    println!("Parsed output is {:?}", r);
  }

  #[test]
  fn verify_parsing2_2() {
    let sample1 = "{USERNAMe}  {TAB} {PASSWORD}              ";

    let mut ph = PlaceHolderParser1::new(sample1);
    let r = ph.parse();
    println!("Parsed output is {:?}", r);
  }

  #[test]
  fn verify_parsing3() {
    let sample1 = "{   TAG      4   }";
    let mut parser = fenced("{", "}");
    let mut parser1 = name_with_delay_or_repeat();

    let (r1, p1) = parser(sample1).unwrap();
    println!("Parsed {:?}", parser(sample1));

    println!("Delay parsed {:?}", parser1(p1));

    //let  parser = nom::combinator::map(parser, |x|x.len());
    //let mut parser =  nom::multi::many1(parser);
    //println!("Parsed output is {:?}", parser(sample1));
  }

  #[test]
  fn verify_parsing4() {
    let sample1 = "{   DELAY   =   4   }";
    let mut parser = fenced("{", "}");
    let mut parser1 = name_with_delay();

    let (r1, p1) = parser(sample1).unwrap();
    println!("Parsed {:?}", parser(sample1));

    println!("Delay parsed {:?}", parser1(p1));
  }

  #[test]
  fn verify_parsing5() {
    let sample1 = "+^+%M {USERNAME}{TITLE}";
    let mut f_parser = fenced("{", "}");
    let mut m_parser = modifier_parser1();

    let (remaing, parsed) = m_parser(sample1).unwrap();
    //let  mut parser = alt((f_parser,m_parser));
    println!("Parsed {:?} with reamining {:?}", parsed, remaing);

    println!("Final parsed {:?}", f_parser(remaing));
  }

  #[test]
  fn experiment1() {
    //let mut parser = separated_pair(alpha1, char(','), alpha1);
    //let mut parser = recognize();

    //let v = parser("abc,def");

    //assert_eq!(parser("abcd;"),Err(Err::Error((";", ErrorKind::Char))));

    //fn parser(input: &str) -> IResult<&str,(&str,&str)>

    fn parser(input: &str) -> IResult<&str, &str> {
      //alpha0(input)
      //recognize(parser)
      //separated_pair(alpha1, char(','), alpha1)(input)
      recognize(separated_pair(alpha1, char(','), alpha1))(input)
    }
    println!("v is {:?}", parser("abcc"));
  }
}


*/
