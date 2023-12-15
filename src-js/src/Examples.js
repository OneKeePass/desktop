import * as React from 'react';
import Badge from '@mui/material/Badge';
import { styled } from '@mui/material/styles';
import IconButton from '@mui/material/IconButton';
import ShoppingCartIcon from '@mui/icons-material/ShoppingCart';

const StyledBadge = styled(Badge)(({ theme }) => ({
  '& .MuiBadge-badge': {
    right: -3,
    top: 13,
    border: `2px solid ${theme.palette.background.paper}`,
    padding: '0 4px',
  },
}));

export function CustomizedBadges() {
  return (
    <IconButton aria-label="cart">
      <StyledBadge badgeContent={5} color="secondary">
        <ShoppingCartIcon />
      </StyledBadge>
    </IconButton>
  );
}


export function myFunction() {
    console.log("myFunction is called from the local package!");
}

export function ExampleComp1() {
  return <h1>Hello World....</h1>;
}


