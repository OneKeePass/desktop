use std::{collections::HashSet, sync::OnceLock};

use nom::{
  branch::alt,
  bytes::complete::{tag, tag_no_case, take_until},
  character::complete::{alpha0, char, multispace0},
  character::complete::{alphanumeric1, digit1, multispace1},
  combinator::{map, map_parser, opt, verify},
  error::Error,
  multi::many1,
  sequence::tuple,
  IResult,
};

static STANDARD_ATTRIBUTES: OnceLock<HashSet<&'static str>> = OnceLock::new();

fn standard_attributes() -> &'static HashSet<&'static str> {
  STANDARD_ATTRIBUTES.get_or_init(|| HashSet::from(["title", "username", "password", "url"]))
}

static KEY_NAMES: OnceLock<HashSet<&'static str>> = OnceLock::new();

fn key_names() -> &'static HashSet<&'static str> {
  KEY_NAMES.get_or_init(|| HashSet::from(["tab", "enter", "space"]))
}

#[derive(Debug, PartialEq, Eq)]
pub enum PlaceHolder<'a> {
  Attribute(&'a str),
  KeyName(&'a str), //TODO: include optional repeat field
  Delay(&'a str),   //TODO: include repeat field
  KeyPressDelay(&'a str), //TODO: include repeat field
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

// Extracts the modifiers if any with the an optional single letter at the end
// parser verify ensures we have only one charater at the end
fn modifier_parser<'a>() -> impl FnMut(&'a str) -> IResult<&'a str, PlaceHolder> {
  map(
    tuple((
      multispace0,
      many1(alt((char('+'), char('^'), char('%'), char('#')))),
      verify(opt(alpha0), |x| x.map_or(true, |v: &str| v.len() == 1)),
      multispace0,
    )),
    |(_, chrs, letter, _)| PlaceHolder::Modfier(chrs),
  )
}

// Extracts the attribute name and verifies that the name is accepted one
fn attribute_parser<'a>() -> impl FnMut(&'a str) -> IResult<&'a str, PlaceHolder> {
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
        // x is a tuple and we check the actula extracted value 
        |x| x.2,
      ),
      move |v: &str| standard_attributes().contains(&v.to_string().to_lowercase().as_str()),
    ),
    // This is the returned from the earlier 'map' parser on verification
    |x| PlaceHolder::Attribute(x),
  )
}

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

// https://stackoverflow.com/questions/70441646/cannot-infer-type-for-type-parameter-error-when-parsing-with-nom

// Gets any key name with any optional repeat value
// Repeat a key  x times (e.g., {SPACE 5} inserts five spaces)
fn key_name_opt_repeat_parser<'a>() -> impl FnMut(&'a str) -> IResult<&'a str, PlaceHolder> {
  map_parser(
    fenced_name(),
    map(
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
            opt(digit1),
            multispace0,
          )),
          |x| (x.1, x.3),
        ),
        |(v, r)| key_names().contains(&v.to_string().to_lowercase().as_str()),
      ),
      |x| PlaceHolder::KeyName(x.0),
    ),
  )
}

// Any mid typing delay (wait) keyword from the sequence - Pause typing for X milliseconds
fn delay_parser<'a>() -> impl FnMut(&'a str) -> IResult<&'a str, PlaceHolder> {
  map_parser(
    fenced_name(),
    map(
      tuple((
        multispace0,
        tag_no_case("delay"),
        multispace1,
        digit1,
        multispace0,
      )),
      |x| PlaceHolder::Delay(x.3),
    ),
  )
}

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

struct PlaceHolderParser<'a> {
  input: &'a str,
}

impl<'a> PlaceHolderParser<'a> {
  fn new(input: &'a str) -> Self {
    Self { input }
  }

  fn parse(&mut self) -> Result<Vec<PlaceHolder>, String> {
    let mut out: Vec<PlaceHolder<'_>> = vec![];
    // Combine all parers that are used to parse a sequence string
    let mut parser = alt((
      modifier_parser(),
      delay_parser(),
      key_delay_parser(),
      attribute_parser(),
      key_name_opt_repeat_parser(),
    ));

    let mut current_input = self.input.trim();
    //IMPORTANT: 
    // After parsing completely the sequence string, the end 'current_input' should be empty
    while !current_input.is_empty() {
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

#[cfg(test)]
mod tests {
  use super::*;
  #[test]
  fn verify_parsing() {
    let sample1 = "^+d  {USERNAMe} {delay 5}  {tab 5} %#M {delay = 10} {SPACE} {PASSWORD} {enter}             ";
    let mut ph = PlaceHolderParser::new(sample1);
    let r = ph.parse();
    println!("Parsed output is {:?}", r);
  }
}

/*

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
